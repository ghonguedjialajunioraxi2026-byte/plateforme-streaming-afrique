package com.streamingafrique.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie CHAQUE endpoint REST de bout en bout (HTTP → Controller → Service
 * → Base H2). La transaction du test est annulée après chaque méthode.
 *
 * Autorisation (étape 6.26) : matrice EndpointTier.
 *   - PUBLIC : lectures (GET) anonymes
 *   - AUTHENTICATED : follows, notifications, upload, profil
 *   - CREATOR : création/publication contenu (chaînes, streams, vidéos, clips)
 *   - MODERATOR : modération (visibilité)
 *   - ADMIN : administration (rôles, suppression comptes)
 *
 * Les comptes sont créés en base avec mot de passe, puis authentifiés en Basic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RESTControllerTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "motdepasse-6.26";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // -------------------------------------------------- helpers

    private User user(String email, User.Role role) {
        User user = User.create(email, email, passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    private static String basic(User account) {
        String raw = account.getEmail() + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private long register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/users")
                        .contentType(JSON).content("""
                                { "email" : "%s", "displayName" : "Tata Clothilde", "password" : "%s" }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    /** Crée la chaîne : nécessite rôle CREATOR (ou supérieur). */
    private long createChannel(User owner, String name) throws Exception {
        String body = mockMvc.perform(post("/api/creators")
                        .header("Authorization", basic(owner))
                        .param("userId", String.valueOf(owner.getId()))
                        .contentType(JSON).content("""
                                { "displayName" : "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    private long read(String pointer, String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        for (String part : pointer.split("\\.")) {
            int bracket = part.indexOf('[');
            if (bracket > 0) {
                node = node.path(part.substring(0, bracket))
                        .path(Integer.parseInt(part.substring(bracket + 1, part.indexOf(']'))));
            } else {
                node = node.path(part);
            }
        }
        return node.asLong();
    }

    // -------------------------------------------------- /api/users

    @Test
    void users_crud_validation_et_role() throws Exception {
        long id = register("ada@example.com");
        User ada = userRepository.findByEmail("ada@example.com").orElseThrow();
        User admin = user("admin@example.com", User.Role.ADMIN);

        mockMvc.perform(post("/api/users").contentType(JSON).content("""
                { "email" : "ADA@example.com", "displayName" : "Doublon" }
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));

        mockMvc.perform(post("/api/users").contentType(JSON).content("""
                { "email" : "ok@example.com", "displayName" : "   " }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.displayName").isNotEmpty());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // SEC-002 : email masqué en lecture anonyme…
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist());

        // …mais visible par la propriétaire elle-même.
        mockMvc.perform(get("/api/users/{id}", id)
                        .header("Authorization", basic(ada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));

        // …et par un ADMIN (support), mais pas par un tiers connecté.
        mockMvc.perform(get("/api/users/{id}", id)
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));

        // seule ADA peut modifier SON compte (AUTHENTICATED)
        mockMvc.perform(put("/api/users/{id}", id)
                        .header("Authorization", basic(ada))
                        .contentType(JSON).content("""
                                { "email" : "ada@example.com", "displayName" : "Ada Lovelace" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada Lovelace"));

        // un ADMIN peut changer le rôle, ADA ne le peut pas
        mockMvc.perform(put("/api/users/{id}/role", id)
                        .header("Authorization", basic(ada))
                        .contentType(JSON).content("""
                                { "role" : "ADMIN" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/{id}/role", id)
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "role" : "CREATOR" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CREATOR"));

        // impossible de s'auto-promouvoir ADMIN (id == acteur)
        mockMvc.perform(put("/api/users/{id}/role", admin.getId())
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "role" : "MODERATOR" }
                                """))
                .andExpect(status().isForbidden());

        // suppression : anonyme → 401, ADMIN → 200
        mockMvc.perform(delete("/api/users/{id}", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/users/{id}", id)
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // -------------------------------------------------- /api/creators

    @Test
    void creators_crud_url_slug_et_erreurs() throws Exception {
        User grace = user("grace@example.com", User.Role.CREATOR);
        long userId = grace.getId();
        long channelId = createChannel(grace, "Étapes & Astuces");

        mockMvc.perform(get("/api/creators/slug/etapes-astuces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId));

        mockMvc.perform(get("/api/creators/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("etapes-astuces"));

        mockMvc.perform(get("/api/creators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(put("/api/creators/{id}", channelId)
                        .header("Authorization", basic(grace))
                        .contentType(JSON).content("""
                                { "displayName" : "Astuces du Dev", "bio" : "Spring Boot" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("astuces-du-dev"));

        // anonyme → 401
        mockMvc.perform(post("/api/creators").contentType(JSON).content("""
                { "displayName" : "Sans user" }
                """))
                .andExpect(status().isUnauthorized());

        // créer une chaîne pour un AUTRE compte est refusé (identité ≠ acteur authentifié)
        User stranger = user("inconnu@example.com", User.Role.CREATOR);
        mockMvc.perform(post("/api/creators")
                        .header("Authorization", basic(stranger))
                        .param("userId", "99999").contentType(JSON).content("""
                                { "displayName" : "Hors planète" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/creators/{id}", channelId)
                        .header("Authorization", basic(grace)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/creators/{id}", channelId))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------- /api/streams

    @Test
    void streams_programmation_cycle_et_filtres() throws Exception {
        User owner = user("stream@example.com", User.Role.CREATOR);
        long channel = createChannel(owner, "Directs");

        long streamId = read("data.id", mockMvc.perform(post("/api/streams")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(channel))
                        .contentType(JSON).content("""
                                { "title" : "Tournoi FIFA", "scheduledStartAt" : "2026-10-01T20:00:00Z" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/streams").param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/streams").param("creatorId", String.valueOf(channel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(post("/api/streams/{id}/start", streamId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LIVE"));

        mockMvc.perform(post("/api/streams/{id}/start", streamId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/streams/{id}/end", streamId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"));

        mockMvc.perform(put("/api/streams/{id}", streamId)
                        .header("Authorization", basic(owner))
                        .contentType(JSON).content("""
                                { "title" : "Tournoi FIFA (ré)programmé" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tournoi FIFA (ré)programmé"));

        mockMvc.perform(delete("/api/streams/{id}", streamId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------- /api/videos

    @Test
    void videos_feed_visibilite_et_crud() throws Exception {
        User owner = user("video@example.com", User.Role.CREATOR);
        long channel = createChannel(owner, "VOD");

        long publicId = read("data.id", mockMvc.perform(post("/api/videos")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(channel))
                        .contentType(JSON).content("""
                                { "title" : "Tuto JPA", "videoUrl" : "https://cdn.example.com/a.mp4", "durationSeconds" : 540 }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long secretId = read("data.id", mockMvc.perform(post("/api/videos")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(channel))
                        .contentType(JSON).content("""
                                { "title" : "Secret", "videoUrl" : "https://cdn.example.com/b.mp4", "durationSeconds" : 10 }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // owner peut changer visibilité (MODERATOR requis - owner est CREATOR, mais service vérifie propriété)
        mockMvc.perform(patch("/api/videos/{id}/visibility", secretId)
                        .header("Authorization", basic(owner))
                        .contentType(JSON).content("""
                                { "visibility" : "PRIVATE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"));

        mockMvc.perform(get("/api/videos/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(put("/api/videos/{id}", publicId)
                        .header("Authorization", basic(owner))
                        .contentType(JSON).content("""
                                { "title" : "Tuto JPA v2", "videoUrl" : "https://cdn.example.com/a.mp4", "durationSeconds" : 600 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Tuto JPA v2"));

        mockMvc.perform(delete("/api/videos/{id}", publicId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/videos/{id}", publicId)).andExpect(status().isNotFound());
    }

    // -------------------------------------------------- /api/clips

    @Test
    void clips_sources_droits_et_feed() throws Exception {
        User owner = user("clip@example.com", User.Role.CREATOR);
        long channel = createChannel(owner, "Clips");
        User otherUser = user("autre@example.com", User.Role.CREATOR);
        long otherChannel = createChannel(otherUser, "Coach");

        long streamId = read("data.id", mockMvc.perform(post("/api/streams")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(channel))
                        .contentType(JSON).content("""
                                { "title" : "Direct à découper" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        long clipId = read("data.id", mockMvc.perform(post("/api/clips/from-stream")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(channel))
                        .param("streamId", String.valueOf(streamId))
                        .contentType(JSON).content("""
                                { "title" : "Meilleur moment", "clipUrl" : "https://cdn.example.com/c.mp4", "durationSeconds" : 25 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.streamId").value(streamId))
                .andExpect(jsonPath("$.data.videoId").doesNotExist())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/clips/from-stream")
                        .header("Authorization", basic(otherUser))
                        .param("creatorId", String.valueOf(otherChannel))
                        .param("streamId", String.valueOf(streamId))
                        .contentType(JSON).content("""
                                { "title" : "Volé", "clipUrl" : "https://evil.com/x.mp4", "durationSeconds" : 5 }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/clips/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(delete("/api/clips/{id}", clipId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------- /api/follows + /api/notifications

    @Test
    void follows_et_notifications_de_bout_en_bout() throws Exception {
        User creator = user("créa@example.com", User.Role.CREATOR);
        long channel = createChannel(creator, "Créa TV");
        User viewer = user("viewer@example.com", User.Role.USER); // USER peut suivre (AUTHENTICATED)

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(creator))
                        .contentType(JSON).content("""
                                { "followerId" : %d, "channelId" : %d }
                                """.formatted(creator.getId(), channel)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(viewer))
                        .contentType(JSON).content("""
                                { "followerId" : %d, "channelId" : %d }
                                """.formatted(viewer.getId(), channel)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.followedId").value(channel));

        mockMvc.perform(get("/api/follows/is-following")
                        .param("followerId", String.valueOf(viewer.getId()))
                        .param("channelId", String.valueOf(channel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/follows/{followerId}/following", viewer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("crea-tv"));

        mockMvc.perform(get("/api/follows/followers-count").param("channelId", String.valueOf(channel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(get("/api/follows/following-count").param("followerId", String.valueOf(viewer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        // SEC-001 : lectures = destinataire authentifié (anonyme → 401).
        mockMvc.perform(get("/api/notifications").param("recipientId", String.valueOf(creator.getId())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/notifications").param("recipientId", String.valueOf(creator.getId()))
                        .header("Authorization", basic(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("NEW_FOLLOWER"));

        mockMvc.perform(get("/api/notifications/unread-count").param("recipientId", String.valueOf(creator.getId()))
                        .header("Authorization", basic(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        // …et jamais celles d'autrui (403 même authentifié).
        mockMvc.perform(get("/api/notifications").param("recipientId", String.valueOf(creator.getId()))
                        .header("Authorization", basic(viewer)))
                .andExpect(status().isForbidden());

        long notifId = read("data.content[0].id", mockMvc.perform(
                        get("/api/notifications").param("recipientId", String.valueOf(creator.getId()))
                                .header("Authorization", basic(creator)))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(patch("/api/notifications/{id}/read", notifId)
                        .header("Authorization", basic(viewer)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/notifications/{id}/read", notifId)
                        .header("Authorization", basic(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));

        mockMvc.perform(post("/api/notifications/{recipientId}/read-all", creator.getId())
                        .header("Authorization", basic(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));

        mockMvc.perform(delete("/api/follows")
                        .header("Authorization", basic(viewer))
                        .param("followerId", String.valueOf(viewer.getId()))
                        .param("channelId", String.valueOf(channel)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/follows/is-following")
                        .param("followerId", String.valueOf(viewer.getId()))
                        .param("channelId", String.valueOf(channel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }
}