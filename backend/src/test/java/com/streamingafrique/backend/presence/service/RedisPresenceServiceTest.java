package com.streamingafrique.backend.presence.service;

import com.streamingafrique.backend.presence.dto.UserPresenceDto;
import com.streamingafrique.backend.presence.model.UserPresence;
import com.streamingafrique.backend.presence.model.UserPresenceStatus;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test du socle de présence (étape 6.21) sur Redis réel (Testcontainers).
 *
 * PRINCIPE central vérifié : la présence est ÉPHÉMÈRE, jamais définitive.
 *   1. connexion   → ONLINE + marqueur en Redis ;
 *   2. déconnexion → OFFLINE (suppression du marqueur) ;
 *   3. multi-sessions → reste ONLINE tant qu'il reste une session ;
 *   4. expiration TTL → repasse OFFLINE sans aucune déconnexion ;
 *   5. nettoyage (sweep) → retire les marqueurs figés/obsolètes ;
 *   6. heartbeat (touch) → prolonge un marqueur actif pour éviter sa mort.
 *
 * Les tâches planifiées sont désactivées (app.presence.enabled=false) : on
 * déclenche sweep/touch à la main pour rester déterministe.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "app.presence.enabled=false")
class RedisPresenceServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("app.redis.ttl", () -> "10s");
        // TTL court (4 s) pour des tests d'expiration rapides.
        registry.add("app.presence.ttl", () -> "4s");
    }

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private RedisCacheService cache;

    @BeforeEach
    void clearPresence() {
        cache.evictByPattern(CacheKeys.PRESENCE_USER_PREFIX + "*");
        for (String member : cache.smembers(CacheKeys.PRESENCE_ONLINE_SET)) {
            cache.srem(CacheKeys.PRESENCE_ONLINE_SET, member);
        }
    }

    @Test
    void connexion_puis_deconnexion_passent_online_puis_offline() {
        presenceService.markOnline(1L);

        assertThat(presenceService.isOnline(1L)).as("connexion → online").isTrue();
        assertThat(presenceService.statusOf(1L)).isEqualTo(UserPresenceStatus.ONLINE);
        assertThat(presenceService.userPresence(1L).lastSeenAt()).isNotNull();
        assertThat(presenceService.countOnline()).isEqualTo(1);
        assertThat(presenceService.onlineUserIds()).containsExactly(1L);
        assertThat(cache.exists(CacheKeys.presenceUser(1L))).as("marqueur stocké").isTrue();

        presenceService.markOffline(1L);

        assertThat(presenceService.isOnline(1L)).as("déconnexion → offline").isFalse();
        assertThat(presenceService.statusOf(1L)).isEqualTo(UserPresenceStatus.OFFLINE);
        assertThat(presenceService.countOnline()).isZero();
        assertThat(cache.exists(CacheKeys.presenceUser(1L))).as("marqueur supprimé").isFalse();
    }

    @Test
    void un_utilisateur_inconnu_est_offline_par_defaut() {
        assertThat(presenceService.statusOf(99L)).isEqualTo(UserPresenceStatus.OFFLINE);
        assertThat(presenceService.isOnline(99L)).isFalse();
        assertThat(presenceService.userPresence(99L).status())
                .isEqualTo(UserPresenceStatus.OFFLINE);
    }

    @Test
    void plusieurs_sessions_restent_online_jusqu_a_la_derniere_fermeture() {
        // Deux onglets/appareils ouverts pour le même utilisateur.
        presenceService.markOnline(7L);
        presenceService.markOnline(7L);

        presenceService.markOffline(7L);
        assertThat(presenceService.isOnline(7L))
                .as("une session fermée mais l'autre ouverte → toujours online").isTrue();

        presenceService.markOffline(7L);
        assertThat(presenceService.isOnline(7L))
                .as("dernière session fermée → offline").isFalse();
        assertThat(presenceService.countOnline()).isZero();
    }

    @Test
    void apres_expiration_ttl_l_utilisateur_redevient_offline() throws InterruptedException {
        presenceService.markOnline(3L);
        assertThat(presenceService.isOnline(3L)).isTrue();

        // Aucune activité, aucune déconnexion : le TTL (4 s) expire tout seul.
        Thread.sleep(5_000);

        assertThat(presenceService.isOnline(3L)).as("TTL expiré → offline").isFalse();
        assertThat(presenceService.countOnline()).as("plus aucun utilisateur compté").isZero();
    }

    @Test
    void touch_prolonge_un_marqueur_actif_et_evite_l_expiration() throws InterruptedException {
        presenceService.markOnline(5L);

        // On simule des heartbeats réguliers (un utilisateur garde sa session ouverte).
        Thread.sleep(2_500);
        presenceService.touch(5L);
        Thread.sleep(2_000);

        // 4,5 s écoulées mais le dernier touch a re-prolongé le TTL.
        assertThat(presenceService.isOnline(5L)).as("heartbeat a prolongé la présence").isTrue();
    }

    @Test
    void sweep_nettoie_les_presences_obsoletes_sans_toucher_les_actives() {
        // Un utilisateur réellement connecté (marqueur frais).
        presenceService.markOnline(1L);

        // Un « zombie » : marqueur FIGÉ (lastSeenAt vieux) resté dans l'ensemble,
        // simulation d'un client qui a disparu sans déconnexion propre.
        cache.putJson(CacheKeys.presenceUser(2L),
                UserPresence.online(2L, 1, Instant.now().minus(Duration.ofMinutes(30))),
                Duration.ofHours(1));
        cache.sadd(CacheKeys.PRESENCE_ONLINE_SET, "2");
        assertThat(presenceService.onlineUserIds()).containsExactlyInAnyOrder(1L, 2L);

        long cleaned = presenceService.sweep();

        assertThat(cleaned).as("un seul zombie nettoyé").isEqualTo(1);
        assertThat(presenceService.isOnline(2L)).as("le zombie est reparti offline").isFalse();
        assertThat(presenceService.isOnline(1L)).as("l'utilisateur actif est intact").isTrue();
        assertThat(presenceService.onlineUserIds()).containsExactly(1L);
    }

    @Test
    void redis_est_connecte_et_service_injecte() {
        assertThat(presenceService).isNotNull();
        assertThat(cache.ping()).isTrue();
    }

    @Test
    void userPresence_renvoie_l_etat_complet() {
        presenceService.markOnline(11L);
        UserPresenceDto dto = presenceService.userPresence(11L);
        assertThat(dto.userId()).isEqualTo(11L);
        assertThat(dto.status()).isEqualTo(UserPresenceStatus.ONLINE);
        assertThat(dto.lastSeenAt()).isNotNull();
        assertThat(List.of(UserPresenceStatus.values())).contains(UserPresenceStatus.AWAY);
    }
}