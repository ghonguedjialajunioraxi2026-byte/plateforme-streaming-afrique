package com.streamingafrique.backend.creator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.creator.dto.CreatorProfileRequest;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test COMPLET du cache Redis (étape 6.19) sur les fiches publiques de
 * créateurs — le scénario cache-aside de bout en bout :
 *
 *   1. première requête  → miss en cache, lecture en base, MISE EN CACHE ;
 *   2. deuxième requête  → servie DEPUIS le cache (snapshot ancien) ;
 *   3. invalidation      → create/update/delete évacuent les clés
 *                          "creators:public:{id}" et "creators:slug:{slug}",
 *                          la requête suivante est re-sourcée depuis la base ;
 *   4. expiration        → après le TTL, la base est relue automatiquement.
 *
 * Redis provient d'un conteneur Testcontainers éphémère ; la base H2 reste
 * la source de vérité.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CreatorCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // TTL court → le test d'expiration est rapide sans attendre 5 minutes.
        registry.add("app.redis.creators-ttl", () -> "2s");
    }

    @Autowired
    private CreatorService creatorService;

    @Autowired
    private CreatorProfileRepository creatorProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisCacheService cache;

    @BeforeEach
    void clearCacheAndData() {
        creatorProfileRepository.deleteAll();
        userRepository.deleteAll();
        cache.evictByPattern("creators:*");
    }

    /** Phase 1 + 2 + 3 : miss → mise en cache → hit → invalidation. */
    @Test
    void premiere_requete_met_en_cache_seconde_est_servie_du_cache_puis_invalidation() {
        User owner = userRepository.save(User.create("ada@example.com", "Ada", null));
        CreatorProfile profile = creatorProfileRepository.save(
                CreatorProfile.create(owner, "Étapes & Astuces", "etapes-astuces"));

        String key = CacheKeys.creatorBySlug("etapes-astuces");
        assertThat(cache.exists(key)).as("cache vide avant la première requête").isFalse();

        // 1) Première requête : miss → lecture base → résultat renvoyé → cache RÉALIMENTÉ
        CreatorProfileDto premierResultat = creatorService.getBySlug("etapes-astuces");
        assertThat(premierResultat.displayName()).isEqualTo("Étapes & Astuces");
        assertThat(premierResultat.slug()).isEqualTo("etapes-astuces");

        // 2) Mise en cache : la clé existe et contient bien un DTO sérialisé
        assertThat(cache.exists(key)).as("la réponse a été mise en cache").isTrue();
        assertThat(cache.getJson(key)).isInstanceOf(CreatorProfileDto.class);

        // On modifie la BASE à l'insu du cache (nom d'affichage seul).
        CreatorProfile dirty = creatorProfileRepository.findById(profile.getId()).orElseThrow();
        dirty.updateChannelInfo("Astuces du Dev", "nouveau bio", null, null);
        creatorProfileRepository.save(dirty);

        // 3) Deuxième requête AVANT validité : servie depuis le cache → snapshot périmé
        CreatorProfileDto depuisLeCache = creatorService.getBySlug("etapes-astuces");
        assertThat(depuisLeCache.displayName())
                .as("servi depuis le cache (modification base invisible)")
                .isEqualTo("Étapes & Astuces");

        // 4) Invalidation : une écriture via le service évacue les clés du créateur
        //    (id + ancien slug "etapes-astuces" + nouveau slug "astuces-du-dev").
        CurrentUser ownerActor = CurrentUser.of(owner.getId(), owner.getEmail(), owner.getRole());
        creatorService.update(profile.getId(),
                new CreatorProfileRequest("Astuces du Dev", "nouveau bio", null, null), ownerActor);

        // La requête suivante est re-sourcée depuis la base, avec le NOUVEAU slug.
        assertThat(cache.exists(key)).as("l'ancien slug a été évacué").isFalse();
        CreatorProfileDto apresInvalidation = creatorService.getBySlug("astuces-du-dev");
        assertThat(apresInvalidation.displayName())
                .as("re-sourcé depuis PostgreSQL après invalidation")
                .isEqualTo("Astuces du Dev");
    }

    /** Phase 4 (variante TTL) : après expiration, la base est relue. */
    @Test
    void apres_expiration_ttl_la_base_est_relue() throws InterruptedException {
        User owner = userRepository.save(User.create("lejeu@example.com", "LeJeu", null));
        creatorProfileRepository.save(CreatorProfile.create(owner, "Le Jeu TV", "le-jeu-tv"));

        // On pré-remplit le cache
        CreatorProfileDto premier = creatorService.getBySlug("le-jeu-tv");
        assertThat(premier.displayName()).isEqualTo("Le Jeu TV");
        assertThat(cache.exists(CacheKeys.creatorBySlug("le-jeu-tv"))).as("cache alimenté").isTrue();

        // Modification de la base « à l'insu » du cache
        CreatorProfile dirty = creatorProfileRepository.findBySlug("le-jeu-tv").orElseThrow();
        dirty.updateChannelInfo("Le Jeu TV V2", null, null, null);
        creatorProfileRepository.save(dirty);

        // Attente de l'EXPIRATION du TTL (2 s)
        Thread.sleep(3_000);

        // Le cache est vide → on repart de la base
        assertThat(cache.exists(CacheKeys.creatorBySlug("le-jeu-tv"))).as("clé expirée").isFalse();

        CreatorProfileDto apresExpiration = creatorService.getBySlug("le-jeu-tv");
        assertThat(apresExpiration.displayName())
                .as("relecture de la base après expiration du TTL")
                .isEqualTo("Le Jeu TV V2");
    }

    @Test
    void redis_est_connecte_et_service_injecte() {
        assertThat(creatorService).isNotNull();
        assertThat(cache.ping()).isTrue();
    }
}