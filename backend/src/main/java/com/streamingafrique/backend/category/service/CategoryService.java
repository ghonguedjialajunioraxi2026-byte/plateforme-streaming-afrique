package com.streamingafrique.backend.category.service;

import java.time.Duration;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.dto.CategoryDto;
import com.streamingafrique.backend.category.dto.CategoryRequest;
import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.common.spec.QuerySpecs;
import com.streamingafrique.backend.common.util.SlugUtil;
import com.streamingafrique.backend.config.RedisCacheProperties;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;

/**
 * La couche Service contient TOUTE la logique métier :
 * règles d'unicité, génération du slug, conversion entity → DTO,
 * gestion des erreurs (ApiException), transactions.
 *
 * Liste : recherche (nom / slug / description), tri et pagination en base.
 *
 * CACHE (exemple complet, étape 6.19) :
 *   La liste publique des catégories est « très consultée » et évolue peu.
 *   Elle est mise en cache (cache-aside) dans Redis avec TTL et validée
 *   sur chaque écriture (create/update/delete). La base reste la SEULE
 *   source de vérité : si Redis est indisponible, tout continue de
 *   fonctionner en lisant directement PostgreSQL.
 *
 * Le controller ne fait qu'acheminer la requête HTTP.
 */
@Service
public class CategoryService {

    private final CategoryRepository repository;
    private final RedisCacheService cache;
    private final Duration categoriesTtl;

    public CategoryService(CategoryRepository repository,
                           RedisCacheService cache,
                           RedisCacheProperties redisProperties) {
        this.repository = repository;
        this.cache = cache;
        this.categoriesTtl = redisProperties.getCategoriesTtl();
    }

    /** Liste publique des catégories, servie depuis le cache si disponible. */
    @Transactional(readOnly = true)
    public ApiPageResponse<CategoryDto> getPublicList(Pageable pageable) {
        String key = CacheKeys.categoriesList(pageable);

        Object cached = cache.getJson(key);
        if (cached instanceof ApiPageResponse<?> page) {
            @SuppressWarnings("unchecked")
            ApiPageResponse<CategoryDto> result = (ApiPageResponse<CategoryDto>) page;
            return result;
        }

        ApiPageResponse<CategoryDto> page = search(null, pageable);
        cache.putJson(key, page, categoriesTtl);
        return page;
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        String name = request.name().trim();
        String slug = SlugUtil.slugify(name);

        if (repository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Une catégorie porte déjà ce nom : " + name);
        }
        if (repository.existsBySlug(slug)) {
            throw ApiException.conflict("Une catégorie conflictuelle existe déjà (slug : " + slug + ")");
        }

        Category category = Category.create(name, slug, request.description());
        CategoryDto dto = toDto(repository.save(category));
        invalidateCategoryLists();
        return dto;
    }

    @Transactional(readOnly = true)
    public CategoryDto getById(Long id) {
        return toDto(findByIdOrThrow(id));
    }

    /** Recherche sur nom/slug/description (OU entre champs), triée et paginée en base. */
    @Transactional(readOnly = true)
    public ApiPageResponse<CategoryDto> search(String search, Pageable pageable) {
        List<Specification<Category>> criteria = List.of(
                QuerySpecs.textContainsAny(List.of("name", "slug", "description"), search));
        Page<Category> page = repository.findAll(QuerySpecs.and(criteria), pageable);
        List<CategoryDto> content = page.getContent().stream().map(this::toDto).toList();
        return ApiPageResponse.from(page, content);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<CategoryDto> getAll(Pageable pageable) {
        return search(null, pageable);
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = findByIdOrThrow(id);
        String name = request.name().trim();
        String slug = SlugUtil.slugify(name);

        if (!category.getName().equalsIgnoreCase(name) && repository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Une catégorie porte déjà ce nom : " + name);
        }
        if (!category.getSlug().equals(slug) && repository.existsBySlug(slug)) {
            throw ApiException.conflict("Une catégorie conflictuelle existe déjà (slug : " + slug + ")");
        }

        category.update(name, slug, request.description());
        CategoryDto dto = toDto(repository.save(category));
        invalidateCategoryLists();
        return dto;
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(findByIdOrThrow(id));
        invalidateCategoryLists();
    }

    /**
     * Invalide TOUTES les pages de la liste publique en cache
     * (pattern "categories:list:*") : c'est plus simple et plus sûr que
     * d'énumérer chaque combinaison page/taille/tri.
     */
    private void invalidateCategoryLists() {
        cache.evictByPattern(CacheKeys.CATEGORY_LIST_PREFIX + "*");
    }

    /** Charge la catégorie ou lève une 404 métier. */
    private Category findByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable (id=" + id + ")"));
    }

    /** Conversion interne entité → DTO de sortie. */
    private CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getSlug(), category.getDescription());
    }
}