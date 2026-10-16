package com.streamingafrique.backend.video.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.dto.VideoRequest;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test COMPLET du cache Redis (étape 6.19) sur les listes très consultées
 * — le feed public des vidéos (page d'accueil) :
 *
 *   1. première requête  → miss en cache, lecture en base, MISE EN CACHE ;
 *   2. deuxième requête  → servie DEPUIS le cache (snapshot ancien) ;
 *   3. invalidation      → publish/update/changeVisibility/delete évacuent
 *                          "videos:feed:*", la requête suivante est
 *                          re-sourcée depuis la base ;
 *   4. expiration        → après le TTL, la base est relue automatiquement.
 *
 * La page 0 (taille 12, plus récentes d'abord) est cachée ; les recherches
 * et filtres restent en base (trop variables pour être mis en cache).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class VideoFeedCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // TTL court → le test d'expiration est rapide sans attendre 5 minutes.
        registry.add("app.redis.lists-ttl", () -> "2s");
    }

    @Autowired
    private VideoService videoService;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisCacheService cache;

    /** Page d'accueil : 12 éléments, des plus récentes aux plus anciennes. */
    private static final PageRequest PAGE =
            PageRequest.of(0, 12, Sort.by("createdAt").descending());

    @BeforeEach
    void clearCacheAndData() {
        videoRepository.deleteAll();
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();
        cache.evictByPattern(CacheKeys.VIDEO_FEED_PREFIX + "*");
    }

    /** L'acteur authentifié (utilisateur propriétaire du contenu). */
    private CurrentUser actor(User owner) {
        return CurrentUser.of(owner.getId(), owner.getEmail(), owner.getRole());
    }

    /** Phase 1 + 2 + 3 : miss → mise en cache → hit → invalidation. */
    @Test
    void premiere_requete_met_en_cache_seconde_est_servie_du_cache_puis_invalidation() {
        User owner = userRepository.save(User.create("vod@example.com", "Vod", null));
        CreatorProfile creator = creatorProfileRepository.save(
                CreatorProfile.create(owner, "VOD TV", "vod-tv"));

        // Publication d'une vidéo VIA LE SERVICE → cache encore vide.
        VideoDto premiere = videoService.publish(creator.getId(), new VideoRequest(
                "Première", null, null, "https://cdn.example.com/x.mp4", null, 60), actor(owner));

        String key = CacheKeys.videoFeed(PAGE);
        assertThat(cache.exists(key)).as("cache vide avant la première requête").isFalse();

        // 1) Première requête : miss → lecture base → résultat renvoyé → cache RÉALIMENTÉ
        ApiPageResponse<VideoDto> premierResultat = videoService.getPublicFeed(PAGE);
        assertThat(premierResultat.totalElements()).isEqualTo(1);
        assertThat(premierResultat.content()).extracting(VideoDto::title).containsExactly("Première");

        // 2) Mise en cache : la clé existe et contient bien une page sérialisée
        assertThat(cache.exists(key)).as("la réponse a été mise en cache").isTrue();
        assertThat(cache.getJson(key)).isInstanceOf(ApiPageResponse.class);

        // On ajoute une vidéo à la BASE à l'insu du cache (comme une écriture tierce).
        videoRepository.save(Video.publish(creator, "Deuxième", null, null,
                "https://cdn.example.com/y.mp4", null, 90));

        // 3) Deuxième requête AVANT validité : servie depuis le cache → snapshot périmé
        ApiPageResponse<VideoDto> depuisLeCache = videoService.getPublicFeed(PAGE);
        assertThat(depuisLeCache.totalElements())
                .as("servi depuis le cache (ajout base invisible)")
                .isEqualTo(1);

        // 4) Invalidation : une écriture via le service évacue le feed du cache
        videoService.update(premiere.id(), new VideoRequest(
                "Première v2", null, null, "https://cdn.example.com/x.mp4", null, 61), actor(owner));

        assertThat(cache.exists(key)).as("l'écriture a évacué le feed").isFalse();

        // La requête suivante est re-sourcée depuis la base (les 2 vidéos).
        ApiPageResponse<VideoDto> apresInvalidation = videoService.getPublicFeed(PAGE);
        assertThat(apresInvalidation.totalElements())
                .as("re-sourcé depuis PostgreSQL après invalidation")
                .isEqualTo(2);
    }

    /** Phase 4 (variante TTL) : après expiration, la base est relue. */
    @Test
    void apres_expiration_ttl_la_base_est_relue() throws InterruptedException {
        User owner = userRepository.save(User.create("replay@example.com", "Replay", null));
        CreatorProfile creator = creatorProfileRepository.save(
                CreatorProfile.create(owner, "Replay TV", "replay-tv"));

        videoService.publish(creator.getId(), new VideoRequest(
                "Seule", null, null, "https://cdn.example.com/s.mp4", null, 45), actor(owner));

        // On pré-remplit le cache
        ApiPageResponse<VideoDto> premier = videoService.getPublicFeed(PAGE);
        assertThat(premier.totalElements()).isEqualTo(1);
        assertThat(cache.exists(CacheKeys.videoFeed(PAGE))).as("cache alimenté").isTrue();

        // Une vidéo de plus en base « à l'insu » du cache
        videoRepository.save(Video.publish(creator, "Ajoutée", null, null,
                "https://cdn.example.com/a.mp4", null, 30));

        // Attente de l'EXPIRATION du TTL (2 s)
        Thread.sleep(3_000);

        assertThat(cache.exists(CacheKeys.videoFeed(PAGE))).as("clé expirée").isFalse();

        ApiPageResponse<VideoDto> apresExpiration = videoService.getPublicFeed(PAGE);
        assertThat(apresExpiration.totalElements())
                .as("relecture de la base après expiration du TTL")
                .isEqualTo(2);
    }

    @Test
    void redis_est_connecte_et_service_injecte() {
        assertThat(videoService).isNotNull();
        assertThat(cache.ping()).isTrue();
    }

    /** Modération (étape 6.25) : un MODERATOR privatise une vidéo d'autrui → retiré du feed. */
    @Test
    void moderateur_peut_privatiser_une_video_dautrui() {
        User owner = userRepository.save(User.create("modo@example.com", "Modo", null));
        CreatorProfile creator = creatorProfileRepository.save(
                CreatorProfile.create(owner, "Modo TV", "modo-tv"));

        VideoDto video = videoService.publish(creator.getId(), new VideoRequest(
                "À contrôler", null, null, "https://cdn.example.com/z.mp4", null, 33), actor(owner));
        videoService.getPublicFeed(PAGE); // remplit le cache

        CurrentUser moderator = CurrentUser.of(9999L, "moderateur@example.com", User.Role.MODERATOR);
        videoService.changeVisibility(video.id(), Video.Visibility.PRIVATE, moderator);

        assertThat(cache.exists(CacheKeys.videoFeed(PAGE))).as("feed évacué par modération").isFalse();
        assertThat(videoService.getPublicFeed(PAGE).content()).isEmpty();
    }

    /** Un USER non propriétaire ne peut PAS privatiser la vidéo d'un autre (propriété prouvée). */
    @Test
    void user_simple_ne_peut_pas_privatiser_une_video_dautrui() {
        User owner = userRepository.save(User.create("user@example.com", "User", null));
        CreatorProfile creator = creatorProfileRepository.save(
                CreatorProfile.create(owner, "User TV", "user-tv"));

        VideoDto video = videoService.publish(creator.getId(), new VideoRequest(
                "Privée d'autrui", null, null, "https://cdn.example.com/w.mp4", null, 44), actor(owner));

        CurrentUser stranger = CurrentUser.of(9999L, "inconnu@example.com", User.Role.USER);
        assertThatThrownBy(() ->
                videoService.changeVisibility(video.id(), Video.Visibility.PRIVATE, stranger))
                .isInstanceOf(ApiException.class);
    }
}