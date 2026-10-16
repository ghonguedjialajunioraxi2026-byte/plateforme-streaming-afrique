package com.streamingafrique.backend.stats.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.notification.repository.NotificationRepository;
import com.streamingafrique.backend.creator.dto.CreatorProfileRequest;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.creator.service.CreatorService;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stats.dto.LiveStreamsCountDto;
import com.streamingafrique.backend.stream.dto.StreamDto;
import com.streamingafrique.backend.stream.dto.StreamRequest;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.stream.service.StreamService;
import com.streamingafrique.backend.user.dto.UserDto;
import com.streamingafrique.backend.user.dto.UserRequest;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.user.service.UserService;
import com.streamingafrique.backend.video.repository.VideoRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test COMPLET du cache Redis (étape 6.19) sur les statistiques de courte
 * durée — « nombre de directs en cours » :
 *
 *   1. première requête  → miss en cache, COMPTAGE en base, mise en cache ;
 *   2. deuxième requête  → servie depuis le cache (chiffre = snapshot) ;
 *   3. invalidation      → les transitions start/end du cycle de vie du
 *                          direct évacuent la clé "stats:streams:live",
 *                          la requête suivante recompte depuis la base ;
 *   4. expiration        → après le TTL court (2 s en test, 20 s en prod),
 *                          la base est recomptée automatiquement.
 *
 * Le comptage vient TOUJOURS de la base : Redis ne sert que d'accélération.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class StatsCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // TTL court → le test d'expiration est rapide sans attendre 20 secondes.
        registry.add("app.redis.stats-ttl", () -> "2s");
    }

    @Autowired
    private StatsService statsService;

    @Autowired
    private StreamService streamService;

    @Autowired
    private CreatorService creatorService;

    @Autowired
    private UserService userService;

    @Autowired
    private StreamRepository streamRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private ClipRepository clipRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisCacheService cache;

    @BeforeEach
    void clearCacheAndData() {
        // Ordre FK : notifications (VOD/clips prêts notifient, étape 15),
        // clips et VOD (auto-créées à chaque fin de live) avant directs,
        // chaînes et comptes.
        notificationRepository.deleteAll();
        clipRepository.deleteAll();
        videoRepository.deleteAll();
        streamRepository.deleteAll();
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();
        cache.evictByPattern("stats:*");
        cache.evictByPattern(CacheKeys.STREAM_LIST_PREFIX + "*");
    }

    /** Phase 1 + 2 + 3 : miss → mise en cache → invalidation par transition live. */
    @Test
    void premiere_requete_met_en_cache_puis_transition_start_invalide() {
        String key = CacheKeys.liveStreamsCount();
        assertThat(cache.exists(key)).as("cache vide avant la première requête").isFalse();

        // 1) Première requête : comptage en base (0 direct) → mise en cache
        LiveStreamsCountDto premier = statsService.getLiveStreamsCount();
        assertThat(premier.liveStreams()).isZero();
        assertThat(premier.measuredAt()).isNotNull();

        // 2) Mise en cache : la clé existe et contient le DTO sérialisé
        assertThat(cache.exists(key)).as("le comptage a été mis en cache").isTrue();
        assertThat(cache.getJson(key)).isInstanceOf(LiveStreamsCountDto.class);

        // 3) Un direct est programmé puis LANCÉ via le service.
        //    "schedule" évacue les LISTES, pas la statistique → le cache tient.
        UserDto owner = userService.create(new UserRequest("live@example.com", "Live", null, null));
        CurrentUser actor = CurrentUser.of(owner.id(), owner.email(), owner.role());
        CreatorProfileDto channel = creatorService.create(owner.id(),
                new CreatorProfileRequest("Live TV", null, null, null), actor);
        StreamDto stream = streamService.schedule(channel.id(),
                new StreamRequest("Grand direct", null, null), actor);

        assertThat(cache.exists(key)).as("la statistique reste valide après un simple schedule").isTrue();

        streamService.start(stream.id(), actor);

        // 4) Invalidation : "start" évacue la clé → la suivante recompte en base.
        assertThat(cache.exists(key)).as("start a évacué la statistique").isFalse();

        LiveStreamsCountDto apresStart = statsService.getLiveStreamsCount();
        assertThat(apresStart.liveStreams()).isEqualTo(1);

        // Terminer le direct invalide aussi → le compteur retombe à zéro.
        streamService.end(stream.id(), actor);
        assertThat(cache.exists(key)).as("end a évacué la statistique").isFalse();
        assertThat(statsService.getLiveStreamsCount().liveStreams()).isZero();
    }

    /** Phase 4 (variante TTL) : après expiration, la base est recomptée. */
    @Test
    void apres_expiration_ttl_la_base_est_recomptee() throws InterruptedException {
        // 0 direct : on pré-remplit le cache
        LiveStreamsCountDto premier = statsService.getLiveStreamsCount();
        assertThat(premier.liveStreams()).isZero();
        assertThat(cache.exists(CacheKeys.liveStreamsCount())).as("cache alimenté").isTrue();

        // Un direct LIVE apparaît « à l'insu » du cache (insertion directe en base,
        // sans passer par le service → aucune invalidation déclenchée).
        User owner = userRepository.save(User.create("replay@example.com", "Replay", null));
        CreatorProfile creator = creatorProfileRepository.save(
                CreatorProfile.create(owner, "Replay TV", "replay-tv"));
        Stream liveStream = Stream.schedule(creator, "En direct", null, null);
        liveStream.markStarted();
        streamRepository.save(liveStream);

        // Attente de l'EXPIRATION du TTL (2 s)
        Thread.sleep(3_000);

        assertThat(cache.exists(CacheKeys.liveStreamsCount())).as("clé expirée").isFalse();

        LiveStreamsCountDto apresExpiration = statsService.getLiveStreamsCount();
        assertThat(apresExpiration.liveStreams())
                .as("recomptage de la base après expiration du TTL")
                .isEqualTo(1);
    }

    @Test
    void redis_est_connecte_et_service_injecte() {
        assertThat(statsService).isNotNull();
        assertThat(cache.ping()).isTrue();
    }
}