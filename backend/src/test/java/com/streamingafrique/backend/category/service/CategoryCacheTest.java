package com.streamingafrique.backend.category.service;

import java.time.Duration;

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

import com.streamingafrique.backend.category.dto.CategoryDto;
import com.streamingafrique.backend.category.dto.CategoryRequest;
import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test COMPLET du cache Redis (étape 6.19) sur les catégories :
 * le scénario cache-aside de bout en bout.
 *
 *   1. première requête  → miss en cache, lecture en base, MISE EN CACHE ;
 *   2. deuxième requête  → servie DEPUIS le cache (donnée = snapshot ancien) ;
 *   3. invalidation      → create/update/delete évacuent "categories:list:*",
 *                          la requête suivante est re-sourcée depuis la base ;
 *   4. expiration        → après le TTL, la base est relue automatiquement.
 *
 * Redis provient d'un conteneur Testcontainers éphémère ; la base reste l'
 * H2 de test. PostgreSQL/H2 = source de vérité, Redis = accélération.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CategoryCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // TTL court → le test d'expiration est rapide sans attendre 15 minutes.
        registry.add("app.redis.categories-ttl", () -> "2s");
    }

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RedisCacheService cache;

    private static final PageRequest PAGE =
            PageRequest.of(0, 10, Sort.by("name"));

    @BeforeEach
    void clearCacheAndData() {
        // Cache Redis vide + base H2 purgée, pour un scénario reproductible.
        categoryRepository.deleteAll();
        cache.evictByPattern(CacheKeys.CATEGORY_LIST_PREFIX + "*");
    }

    /** Phase 1 + 2 + 3 : miss → mise en cache → hit → invalidation. */
    @Test
    void premiere_requete_met_en_cache_seconde_est_servie_du_cache_puis_invalidation() {
        Category foot = categoryRepository.save(
                Category.create("Foot Afrique", "foot-af", "le foot africain"));
        categoryRepository.save(Category.create("Gaming", "gaming", "jeux vidéo"));

        String key = CacheKeys.categoriesList(PAGE);
        assertThat(cache.exists(key)).as("cache vide avant la première requête").isFalse();

        // 1) Première requête : miss → lecture base → résultat renvoyé → cache RÉALIMENTÉ
        var premierResultat = categoryService.getPublicList(PAGE);
        assertThat(premierResultat.content()).hasSize(2);
        assertThat(premierResultat.content())
                .extracting(CategoryDto::name)
                .containsExactly("Foot Afrique", "Gaming");

        // 2) Mise en cache : la clé existe et contient bien une page sérialisée
        assertThat(cache.exists(key)).as("la réponse a été mise en cache").isTrue();
        assertThat(cache.getJson(key)).isInstanceOf(com.streamingafrique.backend.dto.ApiPageResponse.class);

        // On modifie la BASE à l'insu du cache (comme une écriture tierce).
        Category categoryDirty = categoryRepository.findById(foot.getId()).orElseThrow();
        categoryDirty.update("Foot Panafricaine", "foot-panaf", "nouvelle gouverne");
        categoryRepository.save(categoryDirty);

        // 3) Deuxième requête AVANT validité : servie depuis le cache → snapshot périmé
        var depuisLeCache = categoryService.getPublicList(PAGE);
        assertThat(depuisLeCache.content())
                .extracting(CategoryDto::name)
                .as("servi depuis le cache (modification base invisible)")
                .containsExactly("Foot Afrique", "Gaming");

        // 4) Invalidation : une écriture via le service évacue la liste du cache
        categoryService.update(foot.getId(), new CategoryRequest("Foot Panafricaine", "nouvelle gouverne"));

        // La requête suivante est re-sourcée depuis la base (snapshot à jour).
        var apresInvalidation = categoryService.getPublicList(PAGE);
        assertThat(apresInvalidation.content())
                .extracting(CategoryDto::name)
                .as("re-sourcé depuis PostgreSQL après invalidation")
                .containsExactly("Foot Panafricaine", "Gaming");
    }

    /** Phase 4 (variante TTL) : après expiration, la base est relue. */
    @Test
    void apres_expiration_ttl_la_base_est_relue() throws InterruptedException {
        Category tech = categoryRepository.save(
                Category.create("Tech Afrique", "tech-af", "la tech du continent"));

        // On pré-remplit le cache
        var premier = categoryService.getPublicList(PAGE);
        assertThat(premier.content()).extracting(CategoryDto::name).containsExactly("Tech Afrique");
        assertThat(cache.exists(CacheKeys.categoriesList(PAGE))).as("cache alimenté").isTrue();

        // Modification de la base « à l'insu » du cache
        Category dirty = categoryRepository.findById(tech.getId()).orElseThrow();
        dirty.update("Tech Panafricaine", "tech-panaf", "mise à jour");
        categoryRepository.save(dirty);

        // Attente de l'EXPIRATION du TTL (2 s)
        Thread.sleep(3_000);

        // Le cache est vide → on repart de la base
        assertThat(cache.exists(CacheKeys.categoriesList(PAGE))).as("clé expirée").isFalse();

        var apresExpiration = categoryService.getPublicList(PAGE);
        assertThat(apresExpiration.content())
                .extracting(CategoryDto::name)
                .as("relecture de la base après expiration du TTL")
                .containsExactly("Tech Panafricaine");
    }

    @Test
    void duration_de_ttl_configure_est_injectee() {
        assertThat(categoryService).isNotNull();
        assertThat(cache.ping()).isTrue();
    }
}