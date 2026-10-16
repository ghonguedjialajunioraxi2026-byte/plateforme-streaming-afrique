package com.streamingafrique.backend.redis;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration Redis : démarre un conteneur Redis éphémère,
 * donc AUCUN serveur Redis local n'est nécessaire pour lancer les tests.
 *
 * Désactivé automatiquement (puis simplement ignoré) si Docker n'est pas
 * disponible sur la machine.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class RedisCacheServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private RedisCacheService cacheService;

    @Test
    void ping_la_connexion_est_operationnelle() {
        assertThat(cacheService.ping()).isTrue();
    }

    @Test
    void put_then_get_relit_la_valeur() {
        cacheService.put("exemple:bonjour", "café de l'Afrique");

        assertThat(cacheService.get("exemple:bonjour")).isEqualTo("café de l'Afrique");
    }

    @Test
    void get_sur_cle_inconnue_renvoie_null() {
        assertThat(cacheService.get("exemple:inexistant")).isNull();
    }

    @Test
    void put_avec_ttl_explicite_est_respecte() throws InterruptedException {
        cacheService.put("exemple:ttl", "bientôt expiré", Duration.ofSeconds(2));

        assertThat(cacheService.get("exemple:ttl")).isEqualTo("bientôt expiré");

        Thread.sleep(3000);

        assertThat(cacheService.get("exemple:ttl")).isNull();
    }

    @Test
    void evict_supprime_la_cle() {
        cacheService.put("exemple:evict", "à supprimer");
        assertThat(cacheService.get("exemple:evict")).isEqualTo("à supprimer");

        cacheService.evict("exemple:evict");

        assertThat(cacheService.get("exemple:evict")).isNull();
    }
}