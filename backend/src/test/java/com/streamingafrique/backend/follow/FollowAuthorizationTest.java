package com.streamingafrique.backend.follow;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stratégie de tests (étape 6.29) — test d'INTÉGRATION ciblé sur la garde
 * {@code requireSelf} des suivis (étape 6.25), chemin HTTP complet.
 *
 * Criticité : suivre / se désabonner AU NOM d'un autre utilisateur serait une
 * usurpation d'identité sociale. Les gardes unitaires sont verrouillées par
 * {@code CurrentUserTest} ; ici on vérifie le CÂBLAGE contrôleur → service
 * (acteur authentifié réellement propagé, 403 métier bien mappée).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowAuthorizationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "motdepasse-6.29";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user(String email, User.Role role) {
        User user = User.create(email, email, passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    private static String basic(User account) {
        String raw = account.getEmail() + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private long createChannel(User owner, String name) throws Exception {
        String body = mockMvc.perform(post("/api/creators")
                        .header("Authorization", basic(owner))
                        .param("userId", String.valueOf(owner.getId()))
                        .contentType(JSON).content("""
                                { "displayName" : "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private String followBody(long followerId, long channelId) {
        return """
                { "followerId" : %d, "channelId" : %d }
                """.formatted(followerId, channelId);
    }

    // -------------------------------------------------- usurpation refusée

    @Test
    @DisplayName("Suivre AU NOM d'un autre utilisateur → 403 (même avec un compte valide)")
    void suivre_au_nom_d_autrui_refuse() throws Exception {
        User creator = user("crea-6.29@example.com", User.Role.CREATOR);
        long channel = createChannel(creator, "Chaîne 6.29");
        User viewer = user("viewer-6.29@example.com", User.Role.USER);
        User other = user("autre-6.29@example.com", User.Role.USER);

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(viewer))
                        .contentType(JSON).content(followBody(other.getId(), channel)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Même un ADMIN ne peut pas suivre au nom d'un autre (abonnements personnels)")
    void admin_ne_suit_pas_au_nom_d_autrui() throws Exception {
        User creator = user("crea-admin-6.29@example.com", User.Role.CREATOR);
        long channel = createChannel(creator, "Chaîne Admin 6.29");
        User viewer = user("viewer-admin-6.29@example.com", User.Role.USER);
        User admin = user("admin-6.29@example.com", User.Role.ADMIN);

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content(followBody(viewer.getId(), channel)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Se désabonner AU NOM d'un autre utilisateur → 403 (le suivi reste intact)")
    void desabonner_au_nom_d_autrui_refuse() throws Exception {
        User creator = user("crea-unf-6.29@example.com", User.Role.CREATOR);
        long channel = createChannel(creator, "Chaîne Unf 6.29");
        User viewer = user("viewer-unf-6.29@example.com", User.Role.USER);

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(viewer))
                        .contentType(JSON).content(followBody(viewer.getId(), channel)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/follows")
                        .header("Authorization", basic(creator))
                        .param("followerId", String.valueOf(viewer.getId()))
                        .param("channelId", String.valueOf(channel)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/follows/is-following")
                        .param("followerId", String.valueOf(viewer.getId()))
                        .param("channelId", String.valueOf(channel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    // -------------------------------------------------- règles métier

    @Test
    @DisplayName("Suivre deux fois = idempotent : même id retourné, un seul abonné compté")
    void suivre_deux_fois_idempotent() throws Exception {
        User creator = user("crea-idem-6.29@example.com", User.Role.CREATOR);
        long channel = createChannel(creator, "Chaîne Idem 6.29");
        User viewer = user("viewer-idem-6.29@example.com", User.Role.USER);

        String first = mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(viewer))
                        .contentType(JSON).content(followBody(viewer.getId(), channel)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(viewer))
                        .contentType(JSON).content(followBody(viewer.getId(), channel)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode firstId = objectMapper.readTree(first).path("data").path("id");
        JsonNode secondId = objectMapper.readTree(second).path("data").path("id");
        assertThat(secondId.asLong()).isEqualTo(firstId.asLong());

        mockMvc.perform(get("/api/follows/followers-count").param("channelId", String.valueOf(channel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    @DisplayName("Suivre sa propre chaîne → 400 ; anonyme → 401")
    void auto_suivi_et_anonyme() throws Exception {
        User creator = user("crea-self-6.29@example.com", User.Role.CREATOR);
        long channel = createChannel(creator, "Chaîne Self 6.29");

        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(creator))
                        .contentType(JSON).content(followBody(creator.getId(), channel)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/follows")
                        .contentType(JSON).content(followBody(999L, channel)))
                .andExpect(status().isUnauthorized());
    }
}
