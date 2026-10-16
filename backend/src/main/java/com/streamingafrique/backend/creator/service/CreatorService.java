package com.streamingafrique.backend.creator.service;

import java.time.Duration;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.service.AdminAuditService;
import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.common.spec.QuerySpecs;
import com.streamingafrique.backend.common.util.SlugUtil;
import com.streamingafrique.backend.config.RedisCacheProperties;
import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.creator.dto.CreatorProfileRequest;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.media.dto.UploadedImageDto;
import com.streamingafrique.backend.media.model.ImageKind;
import com.streamingafrique.backend.media.service.UploadService;
import com.streamingafrique.backend.media.storage.ImageStorage;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import org.springframework.web.multipart.MultipartFile;

/**
 * Fiches créateurs (chaînes).
 *
 * Règles métier portées par le service :
 *   - un compte ne peut avoir qu'UNE fiche créateur (conflit sinon) ;
 *   - le slug est généré depuis le displayName (SlugUtil) et doit être
 *     unique ;
 *   - la création d'une chaîne PROMEUT le compte en rôle CREATOR ;
 *   - la suppression d'une chaîne réduit le compte en rôle USER.
 *
 * Autorisation (étape 6.25) : la chaîne appartient à son créateur. Les actes
 * d'écriture portent l'ACTEUR authentifié ({@link CurrentUser}) et exigent :
 *   - create  → l'acteur crée SA PROPRE chaîne (requireSelf) ;
 *   - update  → le propriétaire de la chaîne OU un ADMIN (requireOwnerOrAdmin) ;
 *   - delete  → le propriétaire de la chaîne OU un ADMIN (FERMETURE logique,
 *     étape 09 : jamais de DELETE physique avec contenu/streams/follows).
 * Plus jamais d'identité « déclarative » : userId vient de l'authentification.
 *
 * Visibilité (étape 09) : les lectures PUBLIQUES (id, slug, listes) excluent
 * les chaînes CLOSED (404 / absentes des pages) ; le propriétaire gère la
 * sienne via getByUserId/update/delete (checks de propriété).
 *
 * Enrichissement (étape 09) : followerCount calculé (jamais stocké),
 * catégorie principale, statut — DTO versionné en cache v2.
 *
 * Liste : recherche (nom, slug, bio) + tri + pagination en base.
 *
 * CACHE (étape 6.19) :
 *   Les fiches publiques (par id et par slug) sont des « infos publiques
 *   peu changeantes et très consultées » (URL publique "@slug"). Lectures
 *   en cache-aside avec {@code creatorsTtl}, invalidées sur chaque
 *   écriture (create/update/delete). PostgreSQL reste la source de vérité :
 *   Redis absent → lecture base sans erreur (mode dégradé).
 */
@Service
public class CreatorService {

    private final CreatorProfileRepository repository;
    private final UserRepository userRepository;
    private final RedisCacheService cache;
    private final Duration creatorsTtl;
    private final FollowRepository followRepository;
    private final CategoryRepository categoryRepository;
    private final UploadService uploadService;
    private final ImageStorage imageStorage;
    private final AdminAuditService audit;

    public CreatorService(CreatorProfileRepository repository,
                          UserRepository userRepository,
                          RedisCacheService cache,
                          RedisCacheProperties redisProperties,
                          FollowRepository followRepository,
                          CategoryRepository categoryRepository,
                          UploadService uploadService,
                          ImageStorage imageStorage,
                          AdminAuditService audit) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.cache = cache;
        this.creatorsTtl = redisProperties.getCreatorsTtl();
        this.followRepository = followRepository;
        this.categoryRepository = categoryRepository;
        this.uploadService = uploadService;
        this.imageStorage = imageStorage;
        this.audit = audit;
    }

    @Transactional
    public CreatorProfileDto create(Long userId, CreatorProfileRequest request, CurrentUser actor) {
        CurrentUser.requireSelf(actor, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Compte introuvable (id=" + userId + ")"));
        if (repository.existsByUserId(userId)) {
            throw ApiException.conflict("Ce compte possède déjà une fiche créateur");
        }
        String slug = SlugUtil.slugify(request.displayName());
        if (repository.existsBySlug(slug)) {
            throw ApiException.conflict("Ce nom de chaîne est déjà pris (" + slug + ")");
        }

        CreatorProfile profile = repository.save(
                CreatorProfile.create(user, request.displayName().trim(), slug));
        profile.updateChannelInfo(request.displayName().trim(), request.bio(),
                requireWebUrl(request.avatarUrl(), "avatar"), requireWebUrl(request.bannerUrl(), "bannière"));
        profile.updatePrimaryCategory(resolveCategory(request.primaryCategoryId()));

        // La création d'une chaîne PROMEUT un USER en CREATOR, mais ne doit
        // JAMAIS rétrograder un MODERATOR ou un ADMIN (hiérarchie 6.26).
        if (!user.getRole().atLeast(User.Role.CREATOR)) {
            user.changeRole(User.Role.CREATOR);
            userRepository.save(user);
        }

        CreatorProfileDto dto = toDto(profile);
        invalidateCreator(profile);
        return dto;
    }

    /** Fiche publique par identifiant, servie depuis le cache si disponible. */
    @Transactional(readOnly = true)
    public CreatorProfileDto getById(Long id) {
        String key = CacheKeys.creator(id);

        Object cached = cache.getJson(key);
        if (cached instanceof CreatorProfileDto dto) {
            return dto;
        }

        CreatorProfileDto dto = toDto(findVisibleByIdOrThrow(id));
        cache.putJson(key, dto, creatorsTtl);
        return dto;
    }

    /** Récupération par URL publique : "@slug", en cache-aside. */
    @Transactional(readOnly = true)
    public CreatorProfileDto getBySlug(String slug) {
        String key = CacheKeys.creatorBySlug(slug);

        Object cached = cache.getJson(key);
        if (cached instanceof CreatorProfileDto dto) {
            return dto;
        }

        CreatorProfileDto dto = toDto(repository.findBySlug(slug)
                .filter(profile -> profile.getStatus() != CreatorProfile.Status.CLOSED)
                .orElseThrow(() -> ApiException.notFound("Chaîne introuvable : @" + slug)));
        cache.putJson(key, dto, creatorsTtl);
        return dto;
    }

    /** Fiche créateur d'un compte donné. */
    @Transactional(readOnly = true)
    public CreatorProfileDto getByUserId(Long userId) {
        return toDto(repository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Ce compte n'a pas de fiche créateur")));
    }

    /**
     * Ma chaîne (étape 09, §5/§41 équivalent {@code GET /api/channels/me}) :
     * propriétaire dérivé du JWT, jamais d'un {@code userId} frontend.
     */
    @Transactional(readOnly = true)
    public CreatorProfileDto getMyChannel(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        return toDto(repository.findByUserId(actor.id())
                .orElseThrow(() -> ApiException.notFound("Ce compte n'a pas encore de chaîne")));
    }

    /** Recherche sur nom / slug / bio (OU entre champs), triée et paginée en base. */
    @Transactional(readOnly = true)
    public ApiPageResponse<CreatorProfileDto> search(String search, Pageable pageable) {
        List<Specification<CreatorProfile>> criteria = List.of(
                QuerySpecs.textContainsAny(List.of("displayName", "slug", "bio"), search),
                // Les listes publiques excluent les chaînes fermées (soft delete).
                (root, query, cb) -> cb.notEqual(root.get("status"), CreatorProfile.Status.CLOSED));
        Page<CreatorProfile> page = repository.findAll(QuerySpecs.and(criteria), pageable);
        return toApi(page);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<CreatorProfileDto> getAll(Pageable pageable) {
        return search(null, pageable);
    }

    @Transactional
    public CreatorProfileDto update(Long id, CreatorProfileRequest request, CurrentUser actor) {
        CreatorProfile profile = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, profile.getUser().getId());
        String oldSlug = profile.getSlug();
        String name = request.displayName().trim();
        String newSlug = SlugUtil.slugify(name);

        if (!profile.getSlug().equals(newSlug) && repository.existsBySlug(newSlug)) {
            throw ApiException.conflict("Ce nom de chaîne est déjà pris (" + newSlug + ")");
        }

        profile.updateChannelInfo(name, request.bio(),
                requireWebUrl(request.avatarUrl(), "avatar"),
                requireWebUrl(request.bannerUrl(), "bannière"));
        profile.updatePrimaryCategory(resolveCategory(request.primaryCategoryId()));
        if (!profile.getSlug().equals(newSlug)) {
            profile.updateSlug(newSlug);
        }
        CreatorProfileDto dto = toDto(repository.save(profile));
        invalidateCreator(profile, oldSlug);
        return dto;
    }

    /**
     * Avatar de la chaîne (étape 09) : upload sécurisé + liaison + invalidation
     * du cache + suppression de l'ancien fichier (jamais d'orphelin).
     */
    @Transactional
    public CreatorProfileDto updateAvatar(Long id, MultipartFile file, CurrentUser actor) {
        return updateVisual(id, file, actor, ImageKind.AVATAR, true);
    }

    /**
     * Bannière de la chaîne (étape 09) : mêmes garanties que l'avatar.
     * Dimensions RECOMMANDÉES (affichage 16:6) documentées côté frontend ;
     * le backend ne bloque pas sur les dimensions (formats d'écran variés).
     */
    @Transactional
    public CreatorProfileDto updateBanner(Long id, MultipartFile file, CurrentUser actor) {
        return updateVisual(id, file, actor, ImageKind.BANNER, false);
    }

    private CreatorProfileDto updateVisual(Long id, MultipartFile file, CurrentUser actor,
                                           ImageKind kind, boolean avatar) {
        CreatorProfile profile = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, profile.getUser().getId());
        UploadedImageDto uploaded = uploadService.uploadImage(kind, file);
        String oldUrl = avatar ? profile.getAvatarUrl() : profile.getBannerUrl();
        if (avatar) {
            profile.updateChannelInfo(profile.getDisplayName(), profile.getBio(),
                    uploaded.url(), profile.getBannerUrl());
        } else {
            profile.updateChannelInfo(profile.getDisplayName(), profile.getBio(),
                    profile.getAvatarUrl(), uploaded.url());
        }
        CreatorProfileDto dto = toDto(repository.save(profile));
        deleteStoredFile(oldUrl);
        invalidateCreator(profile);
        return dto;
    }

    /**
     * Recherche admin (étape 17) : texte + statut, CLOSED incluses (le public
     * les exclut — l'administration supervise tout). ADMIN au contrôleur.
     */
    @Transactional(readOnly = true)
    public ApiPageResponse<CreatorProfileDto> searchAdmin(String search,
                                                         CreatorProfile.Status status,
                                                         Pageable pageable) {
        String query = search != null && !search.isBlank() ? search.trim() : null;
        return toApi(repository.searchAdmin(query, status, pageable));
    }

    /**
     * Suspension d'une chaîne (étape 17, ADMIN + motif + audit) : ACTIVE →
     * SUSPENDED. La chaîne disparaît du public via le cache invalidé ; ses
     * contenus restent intacts (réactivation possible).
     */
    @Transactional
    public CreatorProfileDto suspend(Long id, String reason, CurrentUser actor) {
        requireAdmin(actor);
        CreatorProfile profile = findByIdOrThrow(id);
        if (actor.id().equals(profile.getUser().getId())) {
            throw ApiException.forbidden("Impossible de suspendre sa propre chaîne ici");
        }
        if (profile.getStatus() != CreatorProfile.Status.ACTIVE) {
            throw ApiException.conflict("Seule une chaîne active peut être suspendue");
        }
        profile.changeStatus(CreatorProfile.Status.SUSPENDED);
        CreatorProfileDto dto = toDto(repository.save(profile));
        invalidateCreator(profile);
        audit.record(actor, "CHAIN_SUSPENDED", "CHAIN", profile.getId(),
                CreatorProfile.Status.ACTIVE.name(),
                CreatorProfile.Status.SUSPENDED.name(), reason);
        return dto;
    }

    /**
     * Réactivation d'une chaîne suspendue (étape 17, ADMIN + motif + audit) :
     * SUSPENDED → ACTIVE. Idempotente en échec propre (409 si non suspendue).
     */
    @Transactional
    public CreatorProfileDto reactivate(Long id, String reason, CurrentUser actor) {
        requireAdmin(actor);
        CreatorProfile profile = findByIdOrThrow(id);
        if (profile.getStatus() != CreatorProfile.Status.SUSPENDED) {
            throw ApiException.conflict("Seule une chaîne suspendue peut être réactivée");
        }
        profile.changeStatus(CreatorProfile.Status.ACTIVE);
        CreatorProfileDto dto = toDto(repository.save(profile));
        invalidateCreator(profile);
        audit.record(actor, "CHAIN_REACTIVATED", "CHAIN", profile.getId(),
                CreatorProfile.Status.SUSPENDED.name(),
                CreatorProfile.Status.ACTIVE.name(), reason);
        return dto;
    }

    /**
     * Fermeture d'une chaîne (étape 09, §30) : SOFT DELETE (statut CLOSED),
     * jamais de DELETE physique — streams, vidéos, follows, abonnements,
     * transactions et modération restent intacts et requêtables.
     */
    @Transactional
    public void delete(Long id, CurrentUser actor) {
        CreatorProfile profile = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, profile.getUser().getId());
        profile.changeStatus(CreatorProfile.Status.CLOSED);
        repository.save(profile);
        // La fermeture ne rétrograde que le rôle promu (CREATOR → USER) ;
        // un MODERATOR ou un ADMIN garde son rang (hiérarchie 6.26).
        if (profile.getUser().getRole() == User.Role.CREATOR) {
            profile.getUser().changeRole(User.Role.USER);
            userRepository.save(profile.getUser());
        }
        invalidateCreator(profile.getId(), profile.getSlug());
    }

    /** Charge la fiche ou lève une 404 métier. */
    private CreatorProfile findByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fiche créateur introuvable (id=" + id + ")"));
    }

    private static void requireAdmin(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Administration réservée");
        }
    }

    /** Variante publique : les chaînes fermées sont introuvables (404, pas 410). */
    private CreatorProfile findVisibleByIdOrThrow(Long id) {
        return repository.findById(id)
                .filter(profile -> profile.getStatus() != CreatorProfile.Status.CLOSED)
                .orElseThrow(() -> ApiException.notFound("Fiche créateur introuvable (id=" + id + ")"));
    }

    /** Catégorie principale : null = retrait, inexistante = 404 (jamais créée ici). */
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable (id=" + categoryId + ")"));
    }

    /**
     * URL d'image acceptée : null/vide (effacement) ou http(s):// ou /uploads/
     * (anti javascript:/data: — XSS stocké via avatar/bannière).
     */
    private static String requireWebUrl(String url, String field) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()
                || trimmed.startsWith("http://")
                || trimmed.startsWith("https://")
                || trimmed.startsWith("/uploads/")) {
            return trimmed.isEmpty() ? null : trimmed;
        }
        throw ApiException.badRequest("L'URL du champ " + field + " doit être http(s):// ou /uploads/...");
    }

    /** Supprime l'ancien fichier S'IL vit dans notre stockage (jamais une URL externe). */
    private void deleteStoredFile(String url) {
        String relative = relativeUploadPath(url);
        if (relative != null) {
            imageStorage.delete(relative);
        }
    }

    /** Extrait le chemin relatif d'une URL /uploads/... — null sinon (URL externe). */
    private static String relativeUploadPath(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("/uploads/")) {
            return null;
        }
        String relative = trimmed.substring("/uploads/".length());
        if (relative.isBlank() || relative.contains("..")) {
            return null;
        }
        return relative;
    }

    /* ─── Invalidation du cache créateur ─────────────────────── */

    /** Invalide les clés publiques (id + slug actuel) d'une fiche. */
    private void invalidateCreator(CreatorProfile profile) {
        invalidateCreator(profile.getId(), profile.getSlug());
    }

    /** Invalide les clés publiques d'une fiche (id + slug — on passe aussi l'ANCEN slug si renommée). */
    private void invalidateCreator(CreatorProfile profile, String oldSlug) {
        invalidateCreator(profile.getId(), profile.getSlug());
        if (!oldSlug.equals(profile.getSlug())) {
            cache.evict(CacheKeys.creatorBySlug(oldSlug));
        }
    }

    private void invalidateCreator(Long id, String slug) {
        cache.evict(CacheKeys.creator(id));
        cache.evict(CacheKeys.creatorBySlug(slug));
    }

    private ApiPageResponse<CreatorProfileDto> toApi(Page<CreatorProfile> page) {
        return ApiPageResponse.from(page,
                page.getContent().stream().map(this::toDto).toList());
    }

    /** Conversion interne entité → DTO (jamais d'entité exposée). */
    private CreatorProfileDto toDto(CreatorProfile profile) {
        CreatorProfileDto.CategoryRef category = profile.getPrimaryCategory() == null ? null
                : new CreatorProfileDto.CategoryRef(profile.getPrimaryCategory().getId(),
                        profile.getPrimaryCategory().getSlug(), profile.getPrimaryCategory().getName());
        return new CreatorProfileDto(profile.getId(), profile.getDisplayName(), profile.getSlug(),
                profile.getBio(), profile.getAvatarUrl(), profile.getBannerUrl(),
                profile.getUser().getId(), profile.getCreatedAt(),
                followRepository.countByFollowedId(profile.getId()),
                category, profile.getStatus());
    }
}