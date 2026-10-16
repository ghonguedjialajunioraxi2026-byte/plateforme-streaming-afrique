package com.streamingafrique.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.config.RedisCacheProperties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Étape 22 — SEC-005 + headers par défaut.
 *
 * <ul>
 *   <li>SEC-005 : avec {@code app.rate-limit.trust-proxy=false} (exposition
 *       directe, sans reverse proxy de confiance), l'en-tête
 *       {@code X-Forwarded-For} — forgeable par le client — est IGNORÉ :
 *       deux requêtes annonçant des IP différentes partagent le même bucket
 *       (adresse TCP réelle) et la seconde est coupée en 429.</li>
 *   <li>Headers : la chaîne Spring Security pose par défaut
 *       {@code X-Content-Type-Options: nosniff} (anti MIME-sniffing, pertinent
 *       pour les avatars/images servis en /uploads).</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.trust-proxy=false",
        "app.rate-limit.public-bucket.requests=1",
        "app.rate-limit.public-bucket.window=1m",
})
class RateLimitTrustProxyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RedisCacheProperties cacheProperties;

    /**
     * Isole le compteur du bucket {@code public} pour l'adresse TCP de MockMvc
     * (127.0.0.1) : Redis est PARTAGÉ entre les classes de tests et les autres
     * suites incrémentent déjà cette clé (lectures publiques sans X-Forwarded-For).
     */
    @BeforeEach
    void resetPublicBucket() {
        redis.delete(cacheProperties.getPrefix() + ":ratelimit:public:ip:127.0.0.1");
    }

    @Test
    @DisplayName("SEC-005 : sans proxy de confiance, X-Forwarded-For forgé ne contourne pas le bucket")
    void forwarded_for_ignore_sans_proxy_de_confiance() throws Exception {
        mockMvc.perform(get("/api/videos/feed")
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isOk());

        // Autre IP déclarée, même connexion TCP (MockMvc) → même bucket → 429.
        mockMvc.perform(get("/api/videos/feed")
                        .header("X-Forwarded-For", "198.51.100.77"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Headers de sécurité par défaut : X-Content-Type-Options nosniff")
    void headers_par_defaut_x_content_type_options() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
