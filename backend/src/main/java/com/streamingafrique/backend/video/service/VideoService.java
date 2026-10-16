package com.streamingafrique.backend.video.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.common.spec.QuerySpecs;
import com.streamingafrique.backend.config.RedisCacheProperties;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.video.config.VideoProperties;
import com.streamingafrique.backend.video.dto.PlaybackResponse;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.dto.VideoRequest;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Vidéos VOD (étape 15 : VOD auto, vues, lecture, modération).
 *
 * Règles métier :
 *   - le PUBLIC ne voit que {@code PUBLIC + READY + publiée + non supprimée}
 *     (feed, recherche, listes, détail anonyme) ; le reste est 404 (on ne
 *     révèle pas l'existence d'un contenu privé/supprimé) ;
 *   - le propriétaire voit et gère les siennes (brouillons inclus, sauf
 *     supprimées) ; l'ADMIN supervise tout ; la modération privatise ;
 *   - les vues sont incrémentées atomiquement après garde anti-abus Redis
 *     (un viewer = une vue par fenêtre, §28-29) ;
 *   - la suppression est LOGIQUE : la VOD reste (historique, clips) et ses
 *     clips passent en PRIVATE (politique §49 documentée : « clips
 *     désactivés », réversibles par le propriétaire).
 *
 * CACHE (étape 6.19, inchangé) : feed non filtré en cache-aside, invalidé
 * sur chaque écriture SAUF les vues (compteur exact au détail, éventuel sur
 * les feeds — TTL court).
 */
@Service
public class VideoService {

    private final VideoRepository repository;
    private final CreatorProfileRepository creatorRepository;
    private final CategoryRepository categoryRepository;
    private final ClipRepository clipRepository;
    private final RedisCacheService cache;
    private final Duration listsTtl;
    private final Duration viewWindow;
    private final ApplicationEventPublisher domainEvents;

    @PersistenceContext
    private EntityManager entities;

    public VideoService(VideoRepository repository,
                        CreatorProfileRepository creatorRepository,
                        CategoryRepository categoryRepository,
                        ClipRepository clipRepository,
                        RedisCacheService cache,
                        RedisCacheProperties redisProperties,
                        VideoProperties videoProperties,
                        ApplicationEventPublisher domainEvents) {
        this.repository = repository;
        this.creatorRepository = creatorRepository;
        this.categoryRepository = categoryRepository;
        this.clipRepository = clipRepository;
        this.cache = cache;
        this.listsTtl = redisProperties.getListsTtl();
        this.viewWindow = videoProperties.getViewWindow();
        this.domainEvents = domainEvents;
    }

    /* ─── Lectures publiques ─────────────────────────────────── */

    /** Feed public, filtrable par recherche/catégorie. Sans filtre → servi depuis le cache. */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> getPublicFeed(String search, String categorySlug, Pageable pageable) {
        return getPublicFeed(search, categorySlug, null, pageable);
    }

    /** Feed public + filtre langue déclarée (étape 19 : découverte par langue). */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> getPublicFeed(String search, String categorySlug,
                                                   String language, Pageable pageable) {
        if (search == null && categorySlug == null && language == null) {
            return publicFeedFromCache(pageable);
        }
        return publicFeedFromDb(search, categorySlug, language, pageable);
    }

    /** Feed public par défaut (page d'accueil) — cache-aside. */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> getPublicFeed(Pageable pageable) {
        return publicFeedFromCache(pageable);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> getByCreator(Long creatorId, Pageable pageable) {
        return searchPublic(null, creatorId, null, pageable);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> getByCategory(Long categoryId, Pageable pageable) {
        List<Specification<Video>> criteria = new ArrayList<>(publicOnly());
        criteria.add(QuerySpecs.pathEquals("category.id", categoryId));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    /** Détail anonyme (lien UNLISTED inclus, PRIVATE/supprimée = 404). */
    @Transactional(readOnly = true)
    public VideoDto getById(Long id) {
        return toDto(requireVisible(id, null));
    }

    /** Détail avec acteur (le propriétaire/modérateur voit aussi le PRIVATE). */
    @Transactional(readOnly = true)
    public VideoDto getVisibleById(Long id, CurrentUser actor) {
        return toDto(requireVisible(id, actor));
    }

    /**
     * Recherche combinée (route publique) : search + créateur + catégorie +
     * visibilité demandée — la visibilité n'est honorée que pour l'ADMIN ou
     * le propriétaire de la chaîne filtrée, sinon PUBLIC forcé (§22).
     */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> search(String search, Long creatorId,
                                            String categorySlug, Video.Visibility visibility,
                                            Pageable pageable) {
        return searchAs(search, creatorId, categorySlug, visibility, null, pageable);
    }

    /** Recherche avec acteur (propriétaire/ADMIN voient leurs contenus). */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> searchAs(String search, Long creatorId,
                                              String categorySlug, Video.Visibility visibility,
                                              CurrentUser actor, Pageable pageable) {
        return searchAs(search, creatorId, categorySlug, visibility, null, actor, pageable);
    }

    /** Recherche + langue déclarée (étape 19). */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> searchAs(String search, Long creatorId,
                                              String categorySlug, Video.Visibility visibility,
                                              String language, CurrentUser actor,
                                              Pageable pageable) {
        if (visibility != null && visibility != Video.Visibility.PUBLIC
                && !canSeeRestricted(creatorId, actor)) {
            // Visibilité restreinte demandée sans droit : page vide (ni repli
            // PUBLIC trompeur, ni fuite du contenu privé).
            return ApiPageResponse.of(List.of(),
                    pageable.getPageNumber(), pageable.getPageSize(), 0);
        }
        List<Specification<Video>> criteria = new ArrayList<>();
        criteria.add(QuerySpecs.textContainsAny(List.of("title", "description"), search));
        criteria.add(QuerySpecs.pathEquals("creator.id", creatorId));
        criteria.add(QuerySpecs.relationFieldEquals("category", "slug", categorySlug));
        criteria.add(languageEquals(language));
        if (visibility != null && canSeeRestricted(creatorId, actor)) {
            criteria.add((root, query, cb) -> cb.equal(root.get("visibility"), visibility));
        } else {
            criteria.addAll(publicOnly());
        }
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    /** Toutes (route publique historique) : PUBLIC lisible uniquement. */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> getAll(Pageable pageable) {
        return toApi(repository.findAll(QuerySpecs.and(publicOnly()), pageable), pageable);
    }

    /** MES vidéos (propriétaire : brouillons inclus, supprimées exclues). */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> getMyVideos(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        CreatorProfile channel = creatorRepository.findByUserId(actor.id())
                .orElseThrow(() -> ApiException.notFound("Ce compte n'a pas encore de chaîne"));
        List<Specification<Video>> criteria = List.of(
                QuerySpecs.pathEquals("creator.id", channel.getId()),
                (root, query, cb) -> cb.isFalse(root.get("deleted")));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    /** Supervision (ADMIN : tout, y compris supprimées/privées). */
    @Transactional(readOnly = true)
    public ApiPageResponse<VideoDto> adminList(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Administration réservée");
        }
        return toApi(repository.findAll(pageable), pageable);
    }

    /* ─── Lecture (playback + vues) ──────────────────────────── */

    /** URLs de lecture (visibilité + READY vérifiés, jamais de chemin interne). */
    @Transactional(readOnly = true)
    public PlaybackResponse getPlayback(Long id, CurrentUser actor) {
        Video video = requireVisible(id, actor);
        if (video.getProcessingStatus() != Video.ProcessingStatus.READY) {
            throw ApiException.badRequest("Vidéo en cours de traitement (statut : "
                    + video.getProcessingStatus() + ")");
        }
        return new PlaybackResponse(video.getVideoUrl(), null,
                video.getThumbnailUrl(), video.getDurationSeconds(), 0);
    }

    /**
     * Enregistre UNE vue (étape 15, §28-29) : PUBLIC/UNLISTED + READY
     * uniquement, garde Redis (premier passage dans la fenêtre), incrément
     * atomique. Retourne le compteur exact.
     */
    @Transactional
    public long recordView(Long id, String viewerKey) {
        Video video = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Vidéo introuvable (id=" + id + ")"));
        if (video.isDeleted()
                || video.getVisibility() == Video.Visibility.PRIVATE
                || video.getProcessingStatus() != Video.ProcessingStatus.READY) {
            throw ApiException.notFound("Vidéo introuvable (id=" + id + ")");
        }
        if (viewerKey == null || viewerKey.isBlank()) {
            throw ApiException.badRequest("Identifiant de session requis");
        }
        boolean first = cache.setIfAbsent("views:video:" + id + ":" + viewerKey, "1", viewWindow);
        if (!first) {
            return video.getViewCount();
        }
        repository.incrementViewCount(id);
        entities.refresh(video);
        return video.getViewCount();
    }

    /* ─── Écritures ──────────────────────────────────────────── */

    @Transactional
    public VideoDto publish(Long creatorId, VideoRequest request, CurrentUser actor) {
        CreatorProfile creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Fiche créateur introuvable (id=" + creatorId + ")"));
        CurrentUser.requireOwnerOrAdmin(actor, creator.getUser().getId());
        Video video = Video.publish(creator, request.title().trim(), request.description(),
                categoryOrNull(request.categoryId()), request.videoUrl().trim(),
                request.thumbnailUrl(), request.durationSeconds());
        VideoDto dto = toDto(repository.save(video));
        invalidateVideoFeeds();
        return dto;
    }

    /**
     * VOD AUTOMATIQUE à la fin d'un live (étape 15, §13) : naît en PROCESSING
     * et en brouillon (appelée par {@code StreamService.end}, jamais par le
     * frontend). Durée = fin − début (≥ 1 s), URL = playback du stream (peut
     * être NULL : le worker marquera FAILED, jamais de READY fantôme).
     */
    @Transactional
    public Video createFromStreamEnd(Stream stream) {
        int duration = 1;
        if (stream.getStartedAt() != null && stream.getEndedAt() != null) {
            duration = Math.max(1, (int) (stream.getEndedAt().getEpochSecond()
                    - stream.getStartedAt().getEpochSecond()));
        }
        Video video = Video.autoFromEndedStream(stream.getCreator(), stream,
                stream.getPlaybackUrl(), duration);
        Video saved = repository.save(video);
        invalidateVideoFeeds();
        // Traitement APRÈS commit (listener) : jamais sur une ligne invisible.
        domainEvents.publishEvent(new VideoProcessingRequested(saved.getId()));
        return saved;
    }

    @Transactional
    public VideoDto update(Long id, VideoRequest request, CurrentUser actor) {
        Video video = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, video.getCreator().getUser().getId());
        video.updateContent(request.title().trim(), request.description(),
                categoryOrNull(request.categoryId()), request.thumbnailUrl(),
                request.durationSeconds());
        VideoDto dto = toDto(repository.save(video));
        invalidateVideoFeeds();
        return dto;
    }

    /**
     * Changement de visibilité (PUBLIC / UNLISTED / PRIVATE).
     * Modération : un MODERATOR (ou plus) peut PRIVATISER n'importe quelle
     * vidéo (visibilité PUBLIC/UNLISTED → PRIVATE, retrait du feed) ; le
     * propriétaire gère la sienne ; l'ADMIN tout.
     */
    @Transactional
    public VideoDto changeVisibility(Long id, Video.Visibility visibility, CurrentUser actor) {
        Video video = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrModerator(actor, video.getCreator().getUser().getId());
        video.changeVisibility(visibility);
        VideoDto dto = toDto(repository.save(video));
        invalidateVideoFeeds();
        return dto;
    }

    /**
     * Langue déclarée (étape 19 : découverte par langue). Propriétaire OU
     * modérateur ; NULL = non renseignée (jamais bloquant). Alimente le
     * filtre `language` du feed et de la recherche.
     */
    @Transactional
    public VideoDto setLanguage(Long id, String language, CurrentUser actor) {
        Video video = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrModerator(actor, video.getCreator().getUser().getId());
        try {
            video.setLanguage(language);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        VideoDto dto = toDto(repository.save(video));
        invalidateVideoFeeds();
        return dto;
    }

    /**
     * Suppression LOGIQUE (étape 15, §25/§49) : la VOD est masquée (détail =
     * 404) mais conservée (historique, statistiques) ; ses clips passent en
     * PRIVATE (« désactivés », réversibles par le propriétaire) ; le fichier
     * est CONSERVÉ (rétention : purge manuelle future, jamais automatique).
     */
    @Transactional
    public void delete(Long id, CurrentUser actor) {
        Video video = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, video.getCreator().getUser().getId());
        video.markDeleted();
        repository.save(video);
        for (Clip clip : clipRepository.findByVideo(video)) {
            if (!clip.isDeleted() && clip.getVisibility() != Clip.Visibility.PRIVATE) {
                clip.changeVisibility(Clip.Visibility.PRIVATE);
                clipRepository.save(clip);
            }
        }
        invalidateVideoFeeds();
    }

    /**
     * Restauration d'une vidéo masquée (étape 15, §56 : « restaurer si prévu »).
     * Idempotente (déjà visible → succès sans changement). Ne réactive PAS
     * les clips désactivés lors de la suppression (réactivation manuelle,
     * politique explicite) ni ne republie un brouillon.
     */
    @Transactional
    public VideoDto restore(Long id, CurrentUser actor) {
        Video video = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, video.getCreator().getUser().getId());
        if (video.isDeleted()) {
            video.markRestored();
            repository.save(video);
            invalidateVideoFeeds();
        }
        return toDto(video);
    }

    /* ─── Cache du feed public ───────────────────────────────── */

    /** Cache-aside du feed public non filtré (page d'accueil). */
    private ApiPageResponse<VideoDto> publicFeedFromCache(Pageable pageable) {
        String key = CacheKeys.videoFeed(pageable);

        Object cached = cache.getJson(key);
        if (cached instanceof ApiPageResponse<?> page) {
            @SuppressWarnings("unchecked")
            ApiPageResponse<VideoDto> result = (ApiPageResponse<VideoDto>) page;
            return result;
        }

        ApiPageResponse<VideoDto> fromDb = publicFeedFromDb(null, null, pageable);
        cache.putJson(key, fromDb, listsTtl);
        return fromDb;
    }

    /** Requête SQL du feed public (UNIQUEMENT le lisible par tous). */
    private ApiPageResponse<VideoDto> publicFeedFromDb(String search, String categorySlug, Pageable pageable) {
        return publicFeedFromDb(search, categorySlug, null, pageable);
    }

    /** Feed public + langue déclarée (NULL en base = non renseignée, exclue si filtre). */
    private ApiPageResponse<VideoDto> publicFeedFromDb(String search, String categorySlug,
                                                       String language, Pageable pageable) {
        List<Specification<Video>> criteria = new ArrayList<>(publicOnly());
        criteria.add(QuerySpecs.textContainsAny(List.of("title", "description"), search));
        criteria.add(QuerySpecs.relationFieldEquals("category", "slug", categorySlug));
        criteria.add(languageEquals(language));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    /** Égalité insensible à la casse sur la langue déclarée (no-op si vide). */
    static Specification<Video> languageEquals(String language) {
        return (root, query, cb) -> {
            if (language == null || language.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("language")),
                    language.trim().toLowerCase(java.util.Locale.ROOT));
        };
    }

    /** Critères du lisible-par-tous : PUBLIC + READY + publiée + non supprimée. */
    private static List<Specification<Video>> publicOnly() {
        return List.of(
                (root, query, cb) -> cb.equal(root.get("visibility"), Video.Visibility.PUBLIC),
                (root, query, cb) -> cb.equal(root.get("processingStatus"), Video.ProcessingStatus.READY),
                (root, query, cb) -> cb.isFalse(root.get("deleted")),
                (root, query, cb) -> cb.isNotNull(root.get("publishedAt")));
    }

    /** Recherche restreinte autorisée : ADMIN partout, propriétaire sur sa chaîne. */
    private boolean canSeeRestricted(Long creatorId, CurrentUser actor) {
        if (actor == null || !actor.isAuthenticated()) {
            return false;
        }
        if (actor.isAdmin()) {
            return true;
        }
        if (creatorId == null) {
            return false;
        }
        return creatorRepository.findById(creatorId)
                .map(channel -> channel.getUser().getId().equals(actor.id()))
                .orElse(false);
    }

    /** Invalide TOUTES les pages du feed en cache (pattern "videos:feed:*"). */
    public void invalidateVideoFeeds() {
        cache.evictByPattern(CacheKeys.VIDEO_FEED_PREFIX + "*");
    }

    /** Vidéo visible par cet acteur (PRIVATE/supprimée = 404, existence masquée). */
    private Video requireVisible(Long id, CurrentUser actor) {
        Video video = findByIdOrThrow(id);
        if (video.isDeleted()) {
            throw ApiException.notFound("Vidéo introuvable (id=" + id + ")");
        }
        if (video.getVisibility() == Video.Visibility.PRIVATE
                && !canReadPrivate(video, actor)) {
            throw ApiException.notFound("Vidéo introuvable (id=" + id + ")");
        }
        return video;
    }

    private static boolean canReadPrivate(Video video, CurrentUser actor) {
        if (actor == null || !actor.isAuthenticated()) {
            return false;
        }
        if (actor.isAdmin() || actor.isModeratorOrAbove()) {
            return true;
        }
        return actor.id().equals(video.getCreator().getUser().getId());
    }

    private ApiPageResponse<VideoDto> searchPublic(String search, Long creatorId,
                                                   String categorySlug, Pageable pageable) {
        List<Specification<Video>> criteria = new ArrayList<>(publicOnly());
        criteria.add(QuerySpecs.textContainsAny(List.of("title", "description"), search));
        criteria.add(QuerySpecs.pathEquals("creator.id", creatorId));
        criteria.add(QuerySpecs.relationFieldEquals("category", "slug", categorySlug));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    private Category categoryOrNull(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable (id=" + categoryId + ")"));
    }

    private Video findByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Vidéo introuvable (id=" + id + ")"));
    }

    private ApiPageResponse<VideoDto> toApi(Page<Video> page, Pageable pageable) {
        return ApiPageResponse.from(page,
                page.getContent().stream().map(this::toDto).toList());
    }

    private VideoDto toDto(Video video) {
        return VideoDto.from(video);
    }
}
