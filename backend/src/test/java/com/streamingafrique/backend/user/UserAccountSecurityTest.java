package com.streamingafrique.backend.user;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stratégie de tests (étape 6.29) — test d'INTÉGRATION ciblé sur les points
 * critiques du cycle compte/connexion/normalisation, chemin HTTP complet.
 *
 * Criticité : l'email est l'identifiant de connexion ET la clé d'unicité —
 * une normalisation incohérente entre inscription et login crée des doublons
 * ou des comptes inaccessibles. La politique mot de passe et l'interdiction
 * d'agir sur les notifications d'autrui ferment deux autres failles réelles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAccountSecurityTest {

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

    private static String basic(String email) {
        String raw = email + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------- normalisation email

    @Test
    @DisplayName("Inscription en majuscules → email stocké en minuscules")
    void inscription_normalise_en_minuscules() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(JSON).content("""
                                { "email" : "INSCRI-6.29@EXAMPLE.COM", "displayName" : "Inscri", "password" : "%s" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("inscri-6.29@example.com"));
    }

    @Test
    @DisplayName("Login insensible à la casse → 200 + jeton Bearer (normalisation AuthService)")
    void login_insensible_casse() throws Exception {
        user("login-6.29@example.com", User.Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON).content("""
                                { "email" : "LOGIN-6.29@Example.COM", "password" : "%s" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("login-6.29@example.com"));
    }

    @Test
    @DisplayName("Login avec espaces autour de l'email → 400 (validation @Email avant normalisation)")
    void login_avec_espaces_rejete_par_validation() throws Exception {
        user("espaces-6.29@example.com", User.Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(JSON).content("""
                                { "email" : "  espaces-6.29@example.com  ", "password" : "%s" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
    }

    // -------------------------------------------------- politique mot de passe

    @Test
    @DisplayName("Mot de passe trop court (< 8) → 400 VALIDATION_ERROR ciblant le champ password")
    void mot_de_passe_trop_court_rejete() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(JSON).content("""
                                { "email" : "court-6.29@example.com", "displayName" : "Court", "password" : "1234567" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.password").isNotEmpty());
    }

    // -------------------------------------------------- notifications d'autrui

    @Test
    @DisplayName("Marquer TOUT LU pour un autre utilisateur → 403 (même authentifié)")
    void tout_lu_au_nom_d_autrui_refuse() throws Exception {
        User creator = user("crea-notif-6.29@example.com", User.Role.CREATOR);
        String channel = mockMvc.perform(post("/api/creators")
                        .header("Authorization", basic(creator.getEmail()))
                        .param("userId", String.valueOf(creator.getId()))
                        .contentType(JSON).content("""
                                { "displayName" : "Chaîne Notif 6.29" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long channelId = objectMapper.readTree(channel).path("data").path("id").asLong();

        User viewer = user("viewer-notif-6.29@example.com", User.Role.USER);
        mockMvc.perform(post("/api/follows")
                        .header("Authorization", basic(viewer.getEmail()))
                        .contentType(JSON).content("""
                                { "followerId" : %d, "channelId" : %d }
                                """.formatted(viewer.getId(), channelId)))
                .andExpect(status().isCreated());

        // Le suivi a créé une notification NEW_FOLLOWER pour le créateur :
        // le viewer ne peut pas la marquer lue en bloc à sa place.
        mockMvc.perform(post("/api/notifications/{recipientId}/read-all", creator.getId())
                        .header("Authorization", basic(viewer.getEmail())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        // Le destinataire, lui, peut le faire.
        mockMvc.perform(post("/api/notifications/{recipientId}/read-all", creator.getId())
                        .header("Authorization", basic(creator.getEmail())))
                .andExpect(status().isOk());
    }
}
