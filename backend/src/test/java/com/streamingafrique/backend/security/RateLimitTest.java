package com.streamingafrique.backend.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anti-abus (étape 6.27) — comportement RÉEL via le chemin HTTP complet.
 *
 * Limites basses injectées ici (chaque famille a SA limite, jamais une limite
 * unique) ; chaque test utilise une IP cliente distincte (X-Forwarded-For)
 * pour isoler les compteurs Redis entre tests :
 *   - login / inscription / recherche / messages / public → 429 + Retry-After
 *     une fois la limite dépassée ;
 *   - sous la limite, la sécurité 6.26 est inchangée : anonyme → 401,
 *     authentifié sans le rôle → 403 (le 429 ne masque jamais le 401/403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.requests=3",
        "app.rate-limit.login.window=1m",
        "app.rate-limit.register.requests=2",
        "app.rate-limit.register.window=1m",
        "app.rate-limit.search.requests=2",
        "app.rate-limit.search.window=1m",
        "app.rate-limit.messages.requests=2",
        "app.rate-limit.messages.window=1m",
        "app.rate-limit.public-bucket.requests=2",
        "app.rate-limit.public-bucket.window=1m",
        "app.rate-limit.default-bucket.requests=100",
        "app.rate-limit.default-bucket.window=1m",
})
class RateLimitTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "motdepasse-6.27";

    private static final AtomicInteger IP_SEQ = new AtomicInteger(0);

    /**
     * Segment aléatoire par exécution : les compteurs Redis survivent 1 minute
     * entre deux lancements, les IP doivent donc être uniques PAR RUN et pas
     * seulement par test (un compteur qui repart à 0 rejouerait des clés
     * encore chaudes du run précédent).
     */
    private static final int RUN_ID = new java.util.Random().nextInt(200) + 1;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** IP cliente unique par test ET par exécution (isole les compteurs Redis). */
    private static String freshIp() {
        return "10.27." + RUN_ID + "." + IP_SEQ.incrementAndGet();
    }

    private User user(String email, User.Role role) {
        User user = User.create(email, email, passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    private static String basic(User account) {
        String raw = account.getEmail() + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------- login

    @Test
    void login_bloque_en_429_apres_3_tentatives() throws Exception {
        String ip = freshIp();
        String body = """
                { "email" : "inconnu-6.27@test.fr", "password" : "mauvais-mdp" }
                """;

        // Sous la limite : la sécurité répond (ici 401, identifiants invalides).
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        // Limite dépassée → 429 + Retry-After + code machine, AVANT tout contrôle métier.
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    // -------------------------------------------------- inscription

    @Test
    void inscription_bloquee_en_429_apres_2_creations() throws Exception {
        String ip = freshIp();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/users")
                            .header("X-Forwarded-For", ip)
                            .contentType(JSON).content("""
                                    { "email" : "inscrit-%d-%s@test.fr", "displayName" : "Nouveau", "password" : "%s" }
                                    """.formatted(i, ip.replace('.', '-'), PASSWORD)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/users")
                        .header("X-Forwarded-For", ip)
                        .contentType(JSON).content("""
                                { "email" : "inscrit-trop@test.fr", "displayName" : "Trop", "password" : "%s" }
                                """.formatted(PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    // -------------------------------------------------- recherche

    @Test
    void recherche_bloquee_en_429_apres_2_requetes() throws Exception {
        String ip = freshIp();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/streams").param("search", "fifa")
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/streams").param("search", "fifa")
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    // -------------------------------------------------- messages

    @Test
    void publication_evenement_bloquee_en_429_apres_2_messages() throws Exception {
        // SEC-003 : la publication exige un compte authentifié.
        User publisher = user("editeur-6.27@test.fr", User.Role.USER);
        String ip = freshIp();
        String body = """
                { "type" : "CHAT_MESSAGE", "payload" : { "text" : "Bonjour" } }
                """;

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/events/publish")
                            .header("Authorization", basic(publisher))
                            .header("X-Forwarded-For", ip)
                            .contentType(JSON).content(body))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/events/publish")
                        .header("Authorization", basic(publisher))
                        .header("X-Forwarded-For", ip)
                        .contentType(JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void publication_evenement_anonyme_refusee_en_401() throws Exception {
        // SEC-003 : sans authentification, pas d'injection d'événements (même sous la limite).
        mockMvc.perform(post("/api/events/publish")
                        .header("X-Forwarded-For", freshIp())
                        .contentType(JSON).content("""
                                { "type" : "CHAT_MESSAGE", "payload" : { "text" : "Faux" } }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- public sensible

    @Test
    void lecture_publique_sensible_bloquee_en_429_apres_2_requetes() throws Exception {
        String ip = freshIp();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/videos/feed")
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/videos/feed")
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    // -------------------------------------------------- 401/403 inchangés sous la limite

    @Test
    void sous_la_limite_anonyme_recoit_401_et_non_429() throws Exception {
        // Une seule requête (sous la limite public=2) sur route protégée → 401, pas 429.
        mockMvc.perform(get("/api/secure/whoami")
                        .header("X-Forwarded-For", freshIp()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void sous_la_limite_mauvais_role_recoit_403_et_non_429() throws Exception {
        User simpleUser = user("simple-6.27@test.fr", User.Role.USER);
        User target = user("cible-6.27@test.fr", User.Role.USER);

        // Authentifié mais non autorisé (bucket default=100, loin de la limite) → 403.
        ResultActions denied = mockMvc.perform(put("/api/users/{id}/role", target.getId())
                        .header("Authorization", basic(simpleUser))
                        .header("X-Forwarded-For", freshIp())
                        .contentType(JSON).content("""
                                { "role" : "MODERATOR" }
                                """))
                .andExpect(status().isForbidden());
        denied.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
