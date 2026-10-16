package com.streamingafrique.backend.auth;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.auth.model.EmailToken;
import com.streamingafrique.backend.auth.repository.AuthSessionRepository;
import com.streamingafrique.backend.auth.repository.EmailTokenRepository;
import com.streamingafrique.backend.security.jwt.JwtProperties;
import com.streamingafrique.backend.security.jwt.JwtService;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentification complète (étape 08) — chemin HTTP réel via MockMvc.
 *
 * Rate limiting DÉSACTIVÉ ici (couvert par RateLimitTest) : ces tests
 * enchaînent légitimement de nombreux appels auth depuis la même IP.
 * Couvre la matrice §34 : inscription, connexion, JWT, autorisation,
 * refresh, mot de passe, vérification email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.rate-limit.enabled=false")
class AuthFlowTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "MotDePasseFort123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthSessionRepository sessionRepository;
    @Autowired
    private EmailTokenRepository emailTokenRepository;

    private String registerBody(String username, String email, String password, String confirm) {
        return """
                {"username":"%s","email":"%s","password":"%s","confirmPassword":"%s"}\
                """.formatted(username, email, password, confirm);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody(username, email, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return data(result).path("id").asText();
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return data(result);
    }

    private EmailToken issueToken(User user, EmailToken.Type type, Instant expiresAt, String raw) {
        return emailTokenRepository.save(
                EmailToken.issue(user, TokenHash.sha256Hex(raw), type, expiresAt));
    }

    // -------------------------------------------------- inscription

    @Test
    @DisplayName("Inscription valide → 201, rôle USER forcé, profil créé, email non vérifié")
    void register_valide() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("user237", "user237@example.com", PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("user237"))
                .andExpect(jsonPath("$.data.email").value("user237@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andReturn();
        assertThat(data(result).has("passwordHash")).isFalse();
        User saved = userRepository.findByEmail("user237@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Injection role=ADMIN ignorée → compte USER")
    void register_role_injecte_ignore() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("""
                                {"username":"malin","email":"ruse@example.com","password":"%s",\
                                "confirmPassword":"%s","role":"ADMIN"}\
                                """.formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn();
        assertThat(data(result).path("role").asText()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Email déjà pris → 409 CONFLICT (unicité insensible à la casse)")
    void register_email_duplique() throws Exception {
        register("premier", "dup@example.com");
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("second", "DUP@example.com", PASSWORD, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @DisplayName("Pseudo déjà pris → 409 CONFLICT")
    void register_username_duplique() throws Exception {
        register("doublon", "a@example.com");
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("DOUBLON", "b@example.com", PASSWORD, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    @DisplayName("Email invalide / pseudo court / caractères interdits → 400")
    void register_validation() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("okpseudo", "pas-un-email", PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("ab", "ab@example.com", PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("mauvais pseudo!", "c@example.com", PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Mot de passe faible (sans chiffre) ou confirmation différente → 400")
    void register_mot_de_passe() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("faible", "faible@example.com", "motdepasse", "motdepasse")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
        mockMvc.perform(post("/api/auth/register").contentType(JSON)
                        .content(registerBody("diff", "diff@example.com", PASSWORD, "Autre1234!")))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------- connexion

    @Test
    @DisplayName("Connexion valide → 200 + paire de jetons + lastLoginAt")
    void login_valide() throws Exception {
        register("connecte", "connecte@example.com");
        JsonNode tokens = login("connecte@example.com", PASSWORD);
        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.path("expiresIn").asLong()).isPositive();
        assertThat(userRepository.findByEmail("connecte@example.com").orElseThrow().getLastLoginAt())
                .isNotNull();
        assertThat(sessionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Mauvais mot de passe / inconnu → 401 générique (anti-énumération)")
    void login_echec_generique() throws Exception {
        register("victime", "victime@example.com");
        for (String body : new String[]{
                "{\"email\":\"victime@example.com\",\"password\":\"Mauvais123!\"}",
                "{\"email\":\"fantome@example.com\",\"password\":\"Mauvais123!\"}"}) {
            mockMvc.perform(post("/api/auth/login").contentType(JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Email ou mot de passe incorrect"));
        }
    }

    @Test
    @DisplayName("Compte suspendu → 401 (pas de fuite, pas de session)")
    void login_suspendu() throws Exception {
        register("suspendu", "suspendu@example.com");
        User user = userRepository.findByEmail("suspendu@example.com").orElseThrow();
        user.changeStatus(User.Status.SUSPENDED);
        userRepository.saveAndFlush(user);
        mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"suspendu@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        assertThat(sessionRepository.findAll()).isEmpty();
    }

    // -------------------------------------------------- me + JWT

    @Test
    @DisplayName("GET /api/auth/me → 200 sans secret ; anonyme → 401")
    void me() throws Exception {
        register("moi", "moi@example.com");
        String access = login("moi@example.com", PASSWORD).path("accessToken").asText();
        MvcResult result = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("moi"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.emailVerified").value(false))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash").doesNotContain("refreshToken");
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access falsifié → 401 ; refresh présenté en Bearer → 401")
    void jwt_invalides() throws Exception {
        register("jwt", "jwt@example.com");
        JsonNode tokens = login("jwt@example.com", PASSWORD);
        String access = tokens.path("accessToken").asText();
        String refresh = tokens.path("refreshToken").asText();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access + "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access expiré → 401")
    void jwt_expire() throws Exception {
        register("expire", "expire@example.com");
        User user = userRepository.findByEmail("expire@example.com").orElseThrow();
        JwtProperties props = new JwtProperties();
        props.setSecret("cle-de-test-au-moins-32-octets-000000");
        props.setAccessTokenTtl(Duration.ofSeconds(-1));
        String expired = new JwtService(props)
                .generateAccessToken(user.getId(), user.getEmail(), "ROLE_USER");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- refresh / logout / sessions

    @Test
    @DisplayName("Refresh → rotation : nouvelle paire, ancien rejeté ensuite")
    void refresh_rotation() throws Exception {
        register("rotatif", "rotatif@example.com");
        String refresh1 = login("rotatif@example.com", PASSWORD).path("refreshToken").asText();
        MvcResult result = mockMvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String refresh2 = data(result).path("refreshToken").asText();
        assertThat(refresh2).isNotEqualTo(refresh1);
        // Rejeu de l'ancien → 400 (rotation consommée).
        mockMvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh1)))
                .andExpect(status().isBadRequest());
        // Le nouveau fonctionne.
        mockMvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh2)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Refresh révoqué (logout) / inconnu → 400")
    void refresh_revoque() throws Exception {
        register("partant", "partant@example.com");
        String refresh = login("partant@example.com", PASSWORD).path("refreshToken").asText();
        mockMvc.perform(post("/api/auth/logout").contentType(JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"n-importe-quoi\"}"))
                .andExpect(status().isBadRequest());
        // Logout idempotent : token inconnu → 200 quand même.
        mockMvc.perform(post("/api/auth/logout").contentType(JSON)
                        .content("{\"refreshToken\":\"n-importe-quoi\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Sessions : liste + révocation d'autrui → 404")
    void sessions() throws Exception {
        register("seda", "seda@example.com");
        register("sedb", "sedb@example.com");
        String accessA = login("seda@example.com", PASSWORD).path("accessToken").asText();
        String accessB = login("sedb@example.com", PASSWORD).path("accessToken").asText();
        MvcResult list = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + accessA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        long sessionB = data(mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + accessB))
                        .andExpect(status().isOk()).andReturn())
                .path(0).path("id").asLong();
        // A ne peut pas révoquer la session de B (accès horizontal).
        mockMvc.perform(delete("/api/auth/sessions/" + sessionB)
                        .header("Authorization", "Bearer " + accessA))
                .andExpect(status().isNotFound());
        assertThat(data(list).size()).isPositive();
        String body = list.getResponse().getContentAsString();
        assertThat(body).doesNotContain("tokenHash").doesNotContain("refreshToken");
        mockMvc.perform(get("/api/auth/sessions")).andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- mot de passe

    @Test
    @DisplayName("Changement valide → nouveau login OK, ancien KO, sessions révoquées")
    void change_password() throws Exception {
        register("change", "change@example.com");
        String access = login("change@example.com", PASSWORD).path("accessToken").asText();
        String refresh = login("change@example.com", PASSWORD).path("refreshToken").asText();
        mockMvc.perform(post("/api/auth/change-password").contentType(JSON)
                        .header("Authorization", "Bearer " + access)
                        .content("""
                                {"currentPassword":"%s","newPassword":"NouveauFort456!","confirmPassword":"NouveauFort456!"}\
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk());
        // Ancien mot de passe mort, nouveau vivant.
        mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"email\":\"change@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized());
        login("change@example.com", "NouveauFort456!");
        // Ancienne session révoquée par le changement.
        mockMvc.perform(post("/api/auth/refresh").contentType(JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Changement : actuel incorrect / confirmation différente → 400")
    void change_password_erreurs() throws Exception {
        register("change2", "change2@example.com");
        String access = login("change2@example.com", PASSWORD).path("accessToken").asText();
        mockMvc.perform(post("/api/auth/change-password").contentType(JSON)
                        .header("Authorization", "Bearer " + access)
                        .content("""
                                {"currentPassword":"Faux1234!","newPassword":"NouveauFort456!","confirmPassword":"NouveauFort456!"}\
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/change-password").contentType(JSON)
                        .header("Authorization", "Bearer " + access)
                        .content("""
                                {"currentPassword":"%s","newPassword":"NouveauFort456!","confirmPassword":"Autre7890!"}\
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Forgot : réponse générique existant/inexistant + token créé si compte")
    void forgot_password() throws Exception {
        register("oublie", "oublie@example.com");
        for (String email : new String[]{"oublie@example.com", "fantome@example.com"}) {
            mockMvc.perform(post("/api/auth/forgot-password").contentType(JSON)
                            .content("{\"email\":\"%s\"}".formatted(email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value(
                            "Si un compte correspond à cette adresse, un email de récupération sera envoyé"));
        }
        User user = userRepository.findByEmail("oublie@example.com").orElseThrow();
        assertThat(emailTokenRepository.findByUserIdAndType(user.getId(), EmailToken.Type.PASSWORD_RESET))
                .hasSize(1);
    }

    @Test
    @DisplayName("Reset valide → login nouveau OK + rejouage refusé + expiré refusé")
    void reset_password() throws Exception {
        register("reset", "reset@example.com");
        User user = userRepository.findByEmail("reset@example.com").orElseThrow();
        String raw = "token-reset-valide-1234567890abcdef";
        issueToken(user, EmailToken.Type.PASSWORD_RESET, Instant.now().plusSeconds(3600), raw);
        mockMvc.perform(post("/api/auth/reset-password").contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"ResetFort789!","confirmPassword":"ResetFort789!"}\
                                """.formatted(raw)))
                .andExpect(status().isOk());
        login("reset@example.com", "ResetFort789!");
        // Rejouage du même token → 400.
        mockMvc.perform(post("/api/auth/reset-password").contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"AutreFort123!","confirmPassword":"AutreFort123!"}\
                                """.formatted(raw)))
                .andExpect(status().isBadRequest());
        // Token expiré → 400.
        String expired = "token-reset-expire-1234567890abcdef";
        issueToken(user, EmailToken.Type.PASSWORD_RESET, Instant.now().minusSeconds(60), expired);
        mockMvc.perform(post("/api/auth/reset-password").contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"AutreFort123!","confirmPassword":"AutreFort123!"}\
                                """.formatted(expired)))
                .andExpect(status().isBadRequest());
        // Confirmation différente → 400.
        String mismatch = "token-reset-mismatch-1234567890ab";
        issueToken(user, EmailToken.Type.PASSWORD_RESET, Instant.now().plusSeconds(3600), mismatch);
        mockMvc.perform(post("/api/auth/reset-password").contentType(JSON)
                        .content("""
                                {"token":"%s","newPassword":"AutreFort123!","confirmPassword":"PasPareil123!"}\
                                """.formatted(mismatch)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------- vérification email

    @Test
    @DisplayName("Verify valide → emailVerified + rejouage/expiré refusés")
    void verify_email() throws Exception {
        register("verifie", "verifie@example.com");
        User user = userRepository.findByEmail("verifie@example.com").orElseThrow();
        String raw = "token-verify-valide-1234567890abcdef";
        issueToken(user, EmailToken.Type.VERIFY_EMAIL, Instant.now().plusSeconds(3600), raw);
        mockMvc.perform(post("/api/auth/verify-email").contentType(JSON)
                        .content("{\"token\":\"%s\"}".formatted(raw)))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        mockMvc.perform(post("/api/auth/verify-email").contentType(JSON)
                        .content("{\"token\":\"%s\"}".formatted(raw)))
                .andExpect(status().isBadRequest());
        String expired = "token-verify-expire-1234567890abcdef";
        issueToken(user, EmailToken.Type.VERIFY_EMAIL, Instant.now().minusSeconds(60), expired);
        mockMvc.perform(post("/api/auth/verify-email").contentType(JSON)
                        .content("{\"token\":\"%s\"}".formatted(expired)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Resend : générique + nouveau jeton (anciens invalidés)")
    void resend_verification() throws Exception {
        register("renvoi", "renvoi@example.com");
        User user = userRepository.findByEmail("renvoi@example.com").orElseThrow();
        // Un jeton existe déjà (créé à l'inscription) : le renvoi l'invalide et en crée un autre.
        mockMvc.perform(post("/api/auth/resend-verification").contentType(JSON)
                        .content("{\"email\":\"renvoi@example.com\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/resend-verification").contentType(JSON)
                        .content("{\"email\":\"fantome@example.com\"}"))
                .andExpect(status().isOk());
        assertThat(emailTokenRepository.findByUserIdAndType(user.getId(), EmailToken.Type.VERIFY_EMAIL))
                .hasSize(2);
    }
}
