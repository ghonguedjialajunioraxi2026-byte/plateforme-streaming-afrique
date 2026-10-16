package com.streamingafrique.backend.observability;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observabilité (étape 6.28) — vérifiée via le chemin HTTP complet.
 *
 * Corrélation : CHAQUE réponse (succès comme erreur) porte X-Request-Id ;
 * un identifiant fourni par le client est répercuté tel quel, sinon le
 * serveur en génère un unique.
 *
 * Confidentialité : AUCUNE réponse d'erreur ne recopie mot de passe, token
 * ou secret (le mot de passe envoyé au login n'apparaît ni dans le corps
 * 401, ni dans une 400 de validation).
 *
 * Monitoring : /actuator/health et /actuator/info publics (sondes),
 * /actuator/metrics réservé aux authentifiés (pas de fuite de métriques).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservabilityTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "motdepasse-6.28";

    @Autowired
    private MockMvc mockMvc;
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

    // -------------------------------------------------- corrélation

    @Test
    void chaque_reponse_succes_porte_un_identifiant_unique() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String id1 = first.getResponse().getHeader("X-Request-Id");
        String id2 = second.getResponse().getHeader("X-Request-Id");
        assertThat(id1).isNotBlank();
        assertThat(id2).isNotBlank();
        assertThat(id2).isNotEqualTo(id1);
    }

    @Test
    void identifiant_fourni_par_le_client_repercute_tel_quel() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Request-Id", "front-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "front-123"));
    }

    @Test
    void erreurs_401_404_portent_aussi_l_identifiant() throws Exception {
        mockMvc.perform(get("/api/secure/whoami").header("X-Request-Id", "err-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "err-401"));

        // 404 sur une route publique inexistante (démo, sans authentification).
        mockMvc.perform(get("/api/test/route-qui-n-existe-pas").header("X-Request-Id", "err-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "err-404"));
    }

    // -------------------------------------------------- confidentialité

    @Test
    void echec_login_ne_renvoie_jamais_le_mot_de_passe() throws Exception {
        User viewer = user("viewer-6.28@test.fr", User.Role.USER);
        String secretTenté = "MotDePasse-Tres-Secret-6.28";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON).content("""
                                { "email" : "%s", "password" : "%s" }
                                """.formatted(viewer.getEmail(), secretTenté)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(secretTenté);
        assertThat(body).doesNotContain("password");
    }

    @Test
    void erreur_validation_ne_renvoie_jamais_le_mot_de_passe() throws Exception {
        String secret = "MotDePasse-Validation-6.28";

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(JSON).content("""
                                { "email" : "pas-un-email", "displayName" : "Test", "password" : "%s" }
                                """.formatted(secret)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(secret);
    }

    // -------------------------------------------------- monitoring

    @Test
    void sondes_health_et_info_publiques() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void metriques_reservees_aux_authentifies() throws Exception {
        // Anonyme → 401 (pas de fuite de métriques internes).
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());

        // Authentifié → 200.
        User admin = user("admin-6.28@test.fr", User.Role.ADMIN);
        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }
}
