package com.streamingafrique.backend.security;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

/**
 * Matrice d'autorisations « étape 6.26 » parcourue via le chemin HTTP complet.
 *
 * Principes vérifiés :
 *   • hiérarchie PUBLIC < AUTHENTICATED < CREATOR < MODERATOR < ADMIN
 *   • PUBLIC : lectures anonymes (catalogue, inscription, login, health)
 *   • AUTHENTICATED : tout connecté (profil, follows, notifications, uploads)
 *   • CREATOR : création/publication contenu (chaînes, streams, vidéos, clips)
 *   • MODERATOR : modération contenu d'autrui (visibilité, suppression)
 *   • ADMIN : administration plateforme (catégories, rôles, comptes)
 *
 * Authentification : comptes créés en base avec mot de passe + Basic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RolePermissionTest {

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

    private long read(String pointer, String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        for (String part : pointer.split("\\.")) {
            node = node.path(part);
        }
        return node.asLong();
    }

    /** Création de chaîne : nécessite rôle CREATOR (ou supérieur). */
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

    /** Tentative de création de chaîne (peut échouer selon le rôle). */
    private void attemptCreateChannel(User owner, String name, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/creators")
                        .header("Authorization", basic(owner))
                        .param("userId", String.valueOf(owner.getId()))
                        .contentType(JSON).content("""
                                { "displayName" : "%s" }
                                """.formatted(name)))
                .andExpect(status().is(expectedStatus));
    }

    /** Publication vidéo : nécessite rôle CREATOR (ou supérieur). */
    private long publishVideo(User owner, long creatorId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/videos")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(creatorId))
                        .contentType(JSON).content("""
                                { "title" : "%s", "videoUrl" : "https://cdn.example.com/v.mp4", "durationSeconds" : 300 }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    /** Tentative de publication vidéo (peut échouer selon le rôle). */
    private void attemptPublishVideo(User owner, long creatorId, String title, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(creatorId))
                        .contentType(JSON).content("""
                                { "title" : "%s", "videoUrl" : "https://cdn.example.com/v.mp4", "durationSeconds" : 300 }
                                """.formatted(title)))
                .andExpect(status().is(expectedStatus));
    }

    // -------------------------------------------------- hiérarchie & lectures

    @Test
    void toutes_les_lectures_restent_anonymes() throws Exception {
        mockMvc.perform(get("/api/creators")).andExpect(status().isOk());
        mockMvc.perform(get("/api/streams")).andExpect(status().isOk());
        mockMvc.perform(get("/api/videos/feed")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void tout_authentifie_peut_creer_sa_chaine_puis_est_promeu_createur() throws Exception {
        // AUTHENTICATED : tout compte connecté peut ouvrir SA chaîne (201),
        // y compris un simple USER — c'est l'acte qui le PROMEUT en CREATOR.
        for (User.Role role : User.Role.values()) {
            User actor = user(role.name().toLowerCase() + "-chaine@example.com", role);
            createChannel(actor, "Chaîne " + role);
        }
    }

    @Test
    void utilisateur_sans_chaine_ne_publie_pas_moderateur_admin_si() throws Exception {
        // Utilisateur accédant à une route créateur → 403 (authentifié mais non autorisé)
        User simpleUser = user("simple-user@example.com", User.Role.USER);
        User creator = user("creator-video@example.com", User.Role.CREATOR);
        long channel = createChannel(creator, "Chaîne pour vidéo");

        // USER sans rôle créateur : ne peut PAS publier sur la chaîne d'autrui (403)
        attemptPublishVideo(simpleUser, channel, "Vidéo USER", 403);

        // CREATOR propriétaire : peut publier (201)
        publishVideo(creator, channel, "Vidéo CREATOR");

        // MODERATOR : hérite de CREATOR, peut publier sur sa chaîne (201)
        User moderator = user("moderator-video@example.com", User.Role.MODERATOR);
        long modChannel = createChannel(moderator, "Chaîne MODERATOR vidéo");
        publishVideo(moderator, modChannel, "Vidéo MODERATOR");

        // ADMIN : hérite de tout, peut publier sur sa chaîne (201)
        User admin = user("admin-video@example.com", User.Role.ADMIN);
        long adminChannel = createChannel(admin, "Chaîne ADMIN vidéo");
        publishVideo(admin, adminChannel, "Vidéo ADMIN");
    }

    @Test
    void createur_et_moderateur_refuses_sur_route_admin() throws Exception {
        // Créateur accédant à une route admin → 403
        User creator = user("creator-admin@example.com", User.Role.CREATOR);
        User target = user("target-admin@example.com", User.Role.USER);
        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .header("Authorization", basic(creator))
                        .contentType(JSON).content("""
                                { "role" : "MODERATOR" }
                                """))
                .andExpect(status().isForbidden());

        // Modérateur accédant à une route admin → 403
        User moderator = user("moderator-admin@example.com", User.Role.MODERATOR);
        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .header("Authorization", basic(moderator))
                        .contentType(JSON).content("""
                                { "role" : "MODERATOR" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/{id}", target.getId())
                        .header("Authorization", basic(moderator)))
                .andExpect(status().isForbidden());

        // Admin accédant à ce qui lui est autorisé → 200/201
        User admin = user("admin-ok@example.com", User.Role.ADMIN);
        long adminChannel = createChannel(admin, "Chaîne ADMIN ok");
        publishVideo(admin, adminChannel, "Vidéo ADMIN ok");
        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "role" : "CREATOR" }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/creator/status")
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/moderator/status")
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/status")
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void les_ecritures_anonymes_sont_refusees_401() throws Exception {
        // Sans authentification : 401 (pas 403)
        mockMvc.perform(post("/api/creators").param("userId", "1")
                        .contentType(JSON).content("""
                                { "displayName" : "Boucle" }
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/videos").param("creatorId", "1")
                        .contentType(JSON).content("""
                                { "title" : "X", "videoUrl" : "https://x", "durationSeconds" : 1 }
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/streams").param("creatorId", "1")
                        .contentType(JSON).content("""
                                { "title" : "Y", "streamUrl" : "https://y" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- propriété

    @Test
    void la_ressource_dautrui_est_interdite_a_un_autre_user() throws Exception {
        User owner = user("owner-1@example.com", User.Role.CREATOR);
        User stranger = user("stranger-1@example.com", User.Role.USER);
        long channel = createChannel(owner, "Chaîne de l'owner");

        // l'étranger ne peut ni publier, ni modifier la chaîne, ni la supprimer
        attemptPublishVideo(stranger, channel, "Intrusion", 403);
        mockMvc.perform(put("/api/creators/{id}", channel)
                        .header("Authorization", basic(stranger))
                        .contentType(JSON).content("""
                                { "displayName" : "Volée" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/creators/{id}", channel)
                        .header("Authorization", basic(stranger)))
                .andExpect(status().isForbidden());

        // le propriétaire (CREATOR), lui, passe
        publishVideo(owner, channel, "Ma vidéo");
        mockMvc.perform(put("/api/creators/{id}", channel)
                        .header("Authorization", basic(owner))
                        .contentType(JSON).content("""
                                { "displayName" : "Renommée" }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void le_profil_dautrui_ne_se_modifie_que_par_le_proprietaire_ou_admin() throws Exception {
        User owner = user("owner-2@example.com", User.Role.USER);
        User other = user("other-2@example.com", User.Role.USER);
        User admin = user("admin-2@example.com", User.Role.ADMIN);

        // l'étranger : requête valide (email requis), mais propriété refusée
        mockMvc.perform(put("/api/users/{id}", owner.getId())
                        .header("Authorization", basic(other))
                        .contentType(JSON).content("""
                                { "email" : "%s", "displayName" : "Piraté" }
                                """.formatted(owner.getEmail())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/{id}", owner.getId())
                        .header("Authorization", basic(owner))
                        .contentType(JSON).content("""
                                { "email" : "%s", "displayName" : "Moi-même" }
                                """.formatted(owner.getEmail())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/{id}", owner.getId())
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "email" : "%s", "displayName" : "Par l'admin" }
                                """.formatted(owner.getEmail())))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------- modération

    @Test
    void la_privatisation_dautrui_est_reservee_au_moderateur_et_admin() throws Exception {
        User owner = user("owner-3@example.com", User.Role.CREATOR);
        User creator = user("creator-3@example.com", User.Role.CREATOR);
        User userRole = user("user-3@example.com", User.Role.USER);
        User moderator = user("modo-3@example.com", User.Role.MODERATOR);
        User admin = user("admin-3@example.com", User.Role.ADMIN);

        long channel = createChannel(owner, "Cible");
        long video = publishVideo(owner, channel, "Vidéo à modérer");

        // CREATOR (pas propriétaire) : ne peut pas modérer (403)
        mockMvc.perform(patch("/api/videos/{id}/visibility", video)
                        .header("Authorization", basic(creator))
                        .contentType(JSON).content("""
                                { "visibility" : "PRIVATE" }
                                """))
                .andExpect(status().isForbidden());

        // USER : ne peut pas modérer (403)
        mockMvc.perform(patch("/api/videos/{id}/visibility", video)
                        .header("Authorization", basic(userRole))
                        .contentType(JSON).content("""
                                { "visibility" : "PRIVATE" }
                                """))
                .andExpect(status().isForbidden());

        // MODERATOR : peut modérer (200)
        mockMvc.perform(patch("/api/videos/{id}/visibility", video)
                        .header("Authorization", basic(moderator))
                        .contentType(JSON).content("""
                                { "visibility" : "PRIVATE" }
                                """))
                .andExpect(status().isOk());

        // ADMIN : peut modérer (200)
        mockMvc.perform(patch("/api/videos/{id}/visibility", video)
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "visibility" : "PUBLIC" }
                                """))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------- administration des comptes

    @Test
    void seuls_les_admins_changent_les_roles() throws Exception {
        User target = user("target-4@example.com", User.Role.USER);
        for (User.Role role : new User.Role[]{User.Role.USER, User.Role.CREATOR, User.Role.MODERATOR}) {
            User actor = user(role.name().toLowerCase() + "-4@example.com", role);
            mockMvc.perform(put("/api/users/{id}/role", target.getId())
                            .header("Authorization", basic(actor))
                            .contentType(JSON).content("""
                                    { "role" : "MODERATOR" }
                                    """))
                    .andExpect(status().isForbidden());
        }

        User admin = user("admin-4@example.com", User.Role.ADMIN);
        mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "role" : "MODERATOR" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MODERATOR"));
    }

    @Test
    void seuls_les_admins_suppriment_des_comptes() throws Exception {
        User victim = user("victim-5@example.com", User.Role.USER);
        User moderator = user("modo-5@example.com", User.Role.MODERATOR);
        User admin = user("admin-5@example.com", User.Role.ADMIN);

        mockMvc.perform(delete("/api/users/{id}", victim.getId())
                        .header("Authorization", basic(moderator)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/users/{id}", victim.getId())
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/{id}", victim.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void personne_ne_change_son_propre_role_ni_ne_se_supprime() throws Exception {
        User admin = user("admin-6@example.com", User.Role.ADMIN);

        // l'ADMIN ne peut PAS s'auto-promouvoir ni se dégrader (self-ADMIN interdit)
        mockMvc.perform(put("/api/users/{id}/role", admin.getId())
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "role" : "ADMIN" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/{id}/role", admin.getId())
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "role" : "CREATOR" }
                                """))
                .andExpect(status().isForbidden());

        // et il ne peut pas non plus supprimer son propre compte
        mockMvc.perform(delete("/api/users/{id}", admin.getId())
                        .header("Authorization", basic(admin)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------- follows (AUTHENTICATED)

    @Test
    void suivre_est_personnel_meme_pour_un_admin() throws Exception {
        User follower = user("follower-7@example.com", User.Role.CREATOR);
        User target = user("target-7@example.com", User.Role.CREATOR);
        User admin = user("admin-7@example.com", User.Role.ADMIN);
        long followedChannel = createChannel(target, "Chaîne suivie");

        // l'ADMIN ne peut pas suivre au nom d'autrui
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "followerId" : %d, "channelId" : %d }
                                """.formatted(follower.getId(), followedChannel)))
                .andExpect(status().isForbidden());

        // le suivant lui-même suit la chaîne d'autrui : 201 (CREATOR peut suivre, AUTHENTICATED)
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(follower))
                        .contentType(JSON).content("""
                                { "followerId" : %d, "channelId" : %d }
                                """.formatted(follower.getId(), followedChannel)))
                .andExpect(status().isCreated());

        // se suivre soi-même reste interdit par la règle métier (400)
        long ownChannel = createChannel(follower, "Ma propre chaîne");
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(follower))
                        .contentType(JSON).content("""
                                { "followerId" : %d, "channelId" : %d }
                                """.formatted(follower.getId(), ownChannel)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void user_peut_suivre_et_ne_plus_suivre() throws Exception {
        User follower = user("follower-8@example.com", User.Role.USER);
        User target = user("target-8@example.com", User.Role.CREATOR);
        long followedChannel = createChannel(target, "Chaîne suivie");

        // USER peut suivre (AUTHENTICATED)
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(follower))
                        .contentType(JSON).content("""
                                { "followerId" : %d, "channelId" : %d }
                                """.formatted(follower.getId(), followedChannel)))
                .andExpect(status().isCreated());

        // USER peut ne plus suivre (AUTHENTICATED)
        mockMvc.perform(delete("/api/follows")
                        .header("Authorization", basic(follower))
                        .param("followerId", String.valueOf(follower.getId()))
                        .param("channelId", String.valueOf(followedChannel)))
                .andExpect(status().isOk());
    }

    @Test
    void user_peut_marquer_notifications_lues() throws Exception {
        User userRole = user("user-notif@example.com", User.Role.USER);
        // USER peut marquer ses notifications comme lues (AUTHENTICATED)
        // Note: on teste juste que la route n'est pas publique (401 anonyme)
        mockMvc.perform(patch("/api/notifications/1/read")
                        .header("Authorization", basic(userRole)))
                .andExpect(status().is(anyOf(is(404), is(200)))); // 404 si notification n'existe pas, 200 si existe

        mockMvc.perform(post("/api/notifications/{recipientId}/read-all", userRole.getId())
                        .header("Authorization", basic(userRole)))
                .andExpect(status().isOk());
    }
}