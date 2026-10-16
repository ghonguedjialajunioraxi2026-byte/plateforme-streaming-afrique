package com.streamingafrique.backend.clip.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.config.ClipProperties;
import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.ClipRequest;
import com.streamingafrique.backend.clip.dto.CreateClipSegmentRequest;
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
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.dto.PlaybackResponse;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Clips (étape 15 : découpage viewer, segments validés, vues).
 *
 * Règles métier :
 *   - un clip a TOUJOURS UNE SEULE source : un direct OU une vidéo
 *     (contrainte @Check portée aussi par la base) ;
 *   - TOUT compte connecté peut découper une source lisible (§32) :
 *     {@code creator} = chaîne source (propriétaire du contenu),
 *     {@code createdBy} = auteur du découpage (traçabilité §48) ;
 *   - le segment est validé SERVEUR (§34-35) : bornes de la source, durée
 *     dans [min, max] configurables ; le clip réutilise le fichier source
 *     (aucun ré-encodage sans FFmpeg) ;
 *   - le PUBLIC ne voit que {@code PUBLIC + READY + non supprimé} ;
 *   - suppression LOGIQUE (jamais de DELETE avec interactions).
 *
 * CACHE (étape 6.19, inchangé) : feed non filtré en cache-aside, invalidé
 * sur écriture (sauf vues : compteur exact au détail).
 */
@Service
public class ClipService {

    private final ClipRepository repository;
    private final CreatorProfileRepository creatorRepository;
    private final StreamRepository streamRepository;
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final RedisCacheService cache;
    private final Duration listsTtl;
    private final Duration viewWindow;
    private final int minDuration;
    private final int maxDuration;
    private final ApplicationEventPublisher domainEvents;

    @PersistenceContext
    private EntityManager entities;

    public ClipService(ClipRepository repository,
                       CreatorProfileRepository creatorRepository,
                       StreamRepository streamRepository,
                       VideoRepository videoRepository,
                       UserRepository userRepository,
                       RedisCacheService cache,
                       RedisCacheProperties redisProperties,
                       ClipProperties clipProperties,
                       ApplicationEventPublisher domainEvents) {
        this.repository = repository;
        this.creatorRepository = creatorRepository;
        this.streamRepository = streamRepository;
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.cache = cache;
        this.listsTtl = redisProperties.getListsTtl();
        this.viewWindow = clipProperties.getViewWindow();
        this.minDuration = clipProperties.getMinDurationSeconds();
        this.maxDuration = clipProperties.getMaxDurationSeconds();
        this.domainEvents = domainEvents;
    }

    /* ─── Découpage viewer (segments) ────────────────────────── */

    /**
     * Découpe un clip depuis une VOD (étape 15, §34) : tout connecté, source
     * lisible (PUBLIC/UNLISTED + READY). Naît en PROCESSING (worker → READY).
     */
    @Transactional
    public ClipDto createFromVideoSegment(Long videoId, CreateClipSegmentRequest request,
                                          CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> ApiException.notFound("Vidéo introuvable (id=" + videoId + ")"));
        requireClippableVideo(video);
        if (video.getVideoUrl() == null || video.getVideoUrl().isBlank()) {
            throw ApiException.badRequest("Source sans fichier (traitement non terminé)");
        }
        validateSegment(request.startTimeSeconds(), request.durationSeconds(),
                video.getDurationSeconds());
        User cutBy = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        Clip.Visibility visibility = video.getVisibility() == Video.Visibility.PUBLIC
                ? Clip.Visibility.PUBLIC : Clip.Visibility.PRIVATE;
        Clip clip = Clip.segmentOfVideo(video.getCreator(), cutBy, video,
                request.title().trim(), video.getVideoUrl(),
                request.startTimeSeconds(), request.durationSeconds(), visibility);
        clip.setThumbnail(request.thumbnailUrl() != null
                ? request.thumbnailUrl() : video.getThumbnailUrl());
        ClipDto dto = toDto(repository.save(clip));
        invalidateClipFeeds();
        // Traitement APRÈS commit (listener) : jamais sur une ligne invisible.
        domainEvents.publishEvent(new ClipProcessingRequested(dto.id()));
        return dto;
    }

    /**
     * Découpe un clip depuis un direct LIVE ou terminé (étape 15, §33) :
     * bornes validées contre la durée écoulée ; sans URL de lecture
     * (pas d'enregistrement) → 400 (le clipping VOD reste la voie normale).
     */
    @Transactional
    public ClipDto createFromStreamSegment(Long streamId, CreateClipSegmentRequest request,
                                           CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> ApiException.notFound("Direct introuvable (id=" + streamId + ")"));
        if (stream.isDeleted()
                || (stream.getVisibility() != Stream.Visibility.PUBLIC
                    && stream.getVisibility() != Stream.Visibility.UNLISTED)) {
            throw ApiException.notFound("Direct introuvable (id=" + streamId + ")");
        }
        if (stream.getStatus() != Stream.Status.LIVE && stream.getStatus() != Stream.Status.ENDED) {
            throw ApiException.badRequest("Seul un direct en cours ou terminé peut être découpé");
        }
        if (stream.getPlaybackUrl() == null || stream.getPlaybackUrl().isBlank()) {
            throw ApiException.badRequest("Direct sans enregistrement (clipping indisponible)");
        }
        validateSegment(request.startTimeSeconds(), request.durationSeconds(),
                elapsedSeconds(stream));
        User cutBy = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        Clip.Visibility visibility = stream.getVisibility() == Stream.Visibility.PUBLIC
                ? Clip.Visibility.PUBLIC : Clip.Visibility.PRIVATE;
        Clip clip = Clip.segmentOfStream(stream.getCreator(), cutBy, stream,
                request.title().trim(), stream.getPlaybackUrl(),
                request.startTimeSeconds(), request.durationSeconds(), visibility);
        clip.setThumbnail(request.thumbnailUrl() != null
                ? request.thumbnailUrl() : stream.getThumbnailUrl());
        ClipDto dto = toDto(repository.save(clip));
        invalidateClipFeeds();
        // Traitement APRÈS commit (listener) : jamais sur une ligne invisible.
        domainEvents.publishEvent(new ClipProcessingRequested(dto.id()));
        return dto;
    }

    /* ─── Création manuelle chaîne (existante, conservée) ────── */

    /** Crée un clip extrait d'un direct (chaîne source, fichier fourni). */
    @Transactional
    public ClipDto createFromStream(Long authorCreatorId, Long streamId, ClipRequest request, CurrentUser actor) {
        CreatorProfile author = creatorRepository.findById(authorCreatorId)
                .orElseThrow(() -> ApiException.notFound("Fiche créateur introuvable (id=" + authorCreatorId + ")"));
        CurrentUser.requireOwnerOrAdmin(actor, author.getUser().getId());
        Stream stream = streamRepository.findById(streamId)
                .orElseThrow(() -> ApiException.notFound("Direct introuvable (id=" + streamId + ")"));
        if (!stream.getCreator().getId().equals(authorCreatorId)) {
            throw ApiException.forbidden("Seul le créateur du direct peut en faire un clip");
        }
        validateDuration(request.durationSeconds());
        Clip clip = Clip.fromStream(author, stream, request.title().trim(),
                request.clipUrl().trim(), request.durationSeconds());
        clip.setThumbnail(request.thumbnailUrl());
        ClipDto dto = toDto(repository.save(clip));
        invalidateClipFeeds();
        return dto;
    }

    /** Crée un clip extrait d'une vidéo (chaîne source, fichier fourni). */
    @Transactional
    public ClipDto createFromVideo(Long authorCreatorId, Long videoId, ClipRequest request, CurrentUser actor) {
        CreatorProfile author = creatorRepository.findById(authorCreatorId)
                .orElseThrow(() -> ApiException.notFound("Fiche créateur introuvable (id=" + authorCreatorId + ")"));
        CurrentUser.requireOwnerOrAdmin(actor, author.getUser().getId());
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> ApiException.notFound("Vidéo introuvable (id=" + videoId + ")"));
        if (!video.getCreator().getId().equals(authorCreatorId)) {
            throw ApiException.forbidden("Seul le créateur de la vidéo peut en faire un clip");
        }
        validateDuration(request.durationSeconds());
        Clip clip = Clip.fromVideo(author, video, request.title().trim(),
                request.clipUrl().trim(), request.durationSeconds());
        clip.setThumbnail(request.thumbnailUrl());
        ClipDto dto = toDto(repository.save(clip));
        invalidateClipFeeds();
        return dto;
    }

    /* ─── Lectures ───────────────────────────────────────────── */

    /** Détail anonyme (PRIVATE/supprimée = 404). */
    @Transactional(readOnly = true)
    public ClipDto getById(Long id) {
        return toDto(requireVisible(id, null));
    }

    /** Détail avec acteur (propriétaire/modération voit aussi le PRIVATE). */
    @Transactional(readOnly = true)
    public ClipDto getVisibleById(Long id, CurrentUser actor) {
        return toDto(requireVisible(id, actor));
    }

    /**
     * Détail par lien court de partage (étape 15, §40-41 : {@code /c/{shareSlug}}).
     * Mêmes règles que la lecture par id : PRIVATE/supprimé = 404 (existence
     * masquée), le propriétaire et la modération voient le PRIVATE connectés.
     */
    @Transactional(readOnly = true)
    public ClipDto getVisibleByShareSlug(String shareSlug, CurrentUser actor) {
        return toDto(requireVisible(findByShareSlugOrThrow(shareSlug), actor));
    }

    /** Recherche combinée (route publique) : search + créateur + source + visibilité. */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> search(String search, Long creatorId, Long streamId,
                                           Long videoId, Clip.Visibility visibility,
                                           Pageable pageable) {
        return searchAs(search, creatorId, streamId, videoId, visibility, null, pageable);
    }

    /** Recherche avec acteur (propriétaire/ADMIN voient leurs contenus). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> searchAs(String search, Long creatorId, Long streamId,
                                             Long videoId, Clip.Visibility visibility,
                                             CurrentUser actor, Pageable pageable) {
        if (visibility != null && visibility != Clip.Visibility.PUBLIC
                && !canSeeRestricted(creatorId, actor)) {
            // Visibilité restreinte demandée sans droit : page vide.
            return ApiPageResponse.of(List.of(),
                    pageable.getPageNumber(), pageable.getPageSize(), 0);
        }
        List<Specification<Clip>> criteria = new ArrayList<>();
        criteria.add(QuerySpecs.textContains("title", search));
        criteria.add(QuerySpecs.pathEquals("creator.id", creatorId));
        criteria.add(QuerySpecs.pathEquals("stream.id", streamId));
        criteria.add(QuerySpecs.pathEquals("video.id", videoId));
        if (visibility != null && canSeeRestricted(creatorId, actor)) {
            criteria.add((root, query, cb) -> cb.equal(root.get("visibility"), visibility));
        } else {
            criteria.addAll(publicOnly());
        }
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> getAll(Pageable pageable) {
        return toApi(repository.findAll(QuerySpecs.and(publicOnly()), pageable), pageable);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> getByCreator(Long creatorId, Pageable pageable) {
        return search(null, creatorId, null, null, null, pageable);
    }

    /** Les clips tirés d'un direct (lisibles uniquement). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> getByStream(Long streamId, Pageable pageable) {
        return search(null, null, streamId, null, null, pageable);
    }

    /** Les clips tirés d'une vidéo (lisibles uniquement). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> getByVideo(Long videoId, Pageable pageable) {
        return search(null, null, null, videoId, null, pageable);
    }

    /** MES clips découpés (tout connecté : auteur du découpage). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> getMyClips(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        List<Specification<Clip>> criteria = List.of(
                (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), actor.id()),
                (root, query, cb) -> cb.isFalse(root.get("deleted")));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    /** Supervision (ADMIN : tout, y compris supprimés/privés). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> adminList(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Administration réservée");
        }
        return toApi(repository.findAll(pageable), pageable);
    }

    /** Feed public (clips visibles par tous), servi depuis le cache. */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> getFeed(Pageable pageable) {
        String key = CacheKeys.clipFeed(pageable);

        Object cached = cache.getJson(key);
        if (cached instanceof ApiPageResponse<?> page) {
            @SuppressWarnings("unchecked")
            ApiPageResponse<ClipDto> result = (ApiPageResponse<ClipDto>) page;
            return result;
        }

        ApiPageResponse<ClipDto> fromDb = toApi(
                repository.findAll(QuerySpecs.and(publicOnly()), pageable), pageable);
        cache.putJson(key, fromDb, listsTtl);
        return fromDb;
    }

    /** Feed public avec recherche — trop variable pour être caché, requête en base. */
    @Transactional(readOnly = true)
    public ApiPageResponse<ClipDto> getFeed(String search, Pageable pageable) {
        if (search == null) {
            return getFeed(pageable);
        }
        List<Specification<Clip>> criteria = new ArrayList<>(publicOnly());
        criteria.add(QuerySpecs.textContains("title", search));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    /* ─── Lecture (playback + vues) ──────────────────────────── */

    /** URLs de lecture (visibilité + READY vérifiés, jamais de chemin interne). */
    @Transactional(readOnly = true)
    public PlaybackResponse getPlayback(Long id, CurrentUser actor) {
        Clip clip = requireVisible(id, actor);
        if (clip.getProcessingStatus() != Clip.ProcessingStatus.READY) {
            throw ApiException.badRequest("Clip en cours de traitement (statut : "
                    + clip.getProcessingStatus() + ")");
        }
        return new PlaybackResponse(clip.getClipUrl(), null,
                clip.getThumbnailUrl(), clip.getDurationSeconds(), clip.getStartsAtSeconds());
    }

    /**
     * Enregistre UNE vue (mêmes règles que les VOD : source lisible + READY,
     * garde Redis, incrément atomique). Retourne le compteur exact.
     */
    @Transactional
    public long recordView(Long id, String viewerKey) {
        Clip clip = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Clip introuvable (id=" + id + ")"));
        if (clip.isDeleted()
                || clip.getVisibility() == Clip.Visibility.PRIVATE
                || clip.getProcessingStatus() != Clip.ProcessingStatus.READY) {
            throw ApiException.notFound("Clip introuvable (id=" + id + ")");
        }
        if (viewerKey == null || viewerKey.isBlank()) {
            throw ApiException.badRequest("Identifiant de session requis");
        }
        boolean first = cache.setIfAbsent("views:clip:" + id + ":" + viewerKey, "1", viewWindow);
        if (!first) {
            return clip.getViewCount();
        }
        repository.incrementViewCount(id);
        entities.refresh(clip);
        return clip.getViewCount();
    }

    /* ─── Écritures ──────────────────────────────────────────── */

    @Transactional
    public ClipDto update(Long id, ClipRequest request, CurrentUser actor) {
        Clip clip = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, clip.getCreator().getUser().getId());
        validateDuration(request.durationSeconds());
        clip.updateContent(request.title().trim(), request.thumbnailUrl(), request.durationSeconds());
        ClipDto dto = toDto(repository.save(clip));
        invalidateClipFeeds();
        return dto;
    }

    /**
     * Changement de visibilité (PUBLIC / UNLISTED / PRIVATE).
     * Modération : un MODERATOR (ou plus) peut PRIVATISER n'importe quel
     * clip (retrait du feed) ; le propriétaire gère le sien ; l'ADMIN tout.
     */
    @Transactional
    public ClipDto changeVisibility(Long id, Clip.Visibility visibility, CurrentUser actor) {
        Clip clip = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrModerator(actor, clip.getCreator().getUser().getId());
        clip.changeVisibility(visibility);
        ClipDto dto = toDto(repository.save(clip));
        invalidateClipFeeds();
        return dto;
    }

    /** Suppression LOGIQUE : masqué (détail = 404) mais conservé. */
    @Transactional
    public void delete(Long id, CurrentUser actor) {
        Clip clip = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrModerator(actor, clip.getCreator().getUser().getId());
        clip.markDeleted();
        repository.save(clip);
        invalidateClipFeeds();
    }

    /**
     * Restauration d'un clip masqué (symétrique VOD, idempotente).
     * Modération incluse (comme la suppression).
     */
    @Transactional
    public ClipDto restore(Long id, CurrentUser actor) {
        Clip clip = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrModerator(actor, clip.getCreator().getUser().getId());
        if (clip.isDeleted()) {
            clip.markRestored();
            repository.save(clip);
            invalidateClipFeeds();
        }
        return toDto(clip);
    }

    /* ─── Règles internes ────────────────────────────────────── */

    /** Segment SERVEUR (§34) : bornes de la source + durée dans [min, max]. */
    private void validateSegment(int startSeconds, int durationSeconds, int sourceDurationSeconds) {
        validateDuration(durationSeconds);
        if (startSeconds < 0 || startSeconds + durationSeconds > sourceDurationSeconds) {
            throw ApiException.badRequest("Segment hors limites (source : "
                    + sourceDurationSeconds + " s)");
        }
    }

    /** Durée d'extrait dans [min, max] configurables (§30). */
    private void validateDuration(int durationSeconds) {
        if (durationSeconds < minDuration || durationSeconds > maxDuration) {
            throw ApiException.badRequest("Durée d'extrait entre " + minDuration
                    + " et " + maxDuration + " secondes");
        }
    }

    private void requireClippableVideo(Video video) {
        if (video.isDeleted()
                || (video.getVisibility() != Video.Visibility.PUBLIC
                    && video.getVisibility() != Video.Visibility.UNLISTED)) {
            throw ApiException.notFound("Vidéo introuvable (id=" + video.getId() + ")");
        }
        if (video.getProcessingStatus() != Video.ProcessingStatus.READY) {
            throw ApiException.badRequest("Vidéo en cours de traitement");
        }
    }

    private static int elapsedSeconds(Stream stream) {
        if (stream.getStartedAt() == null) {
            throw ApiException.badRequest("Direct sans début mesurable");
        }
        long end = stream.getStatus() == Stream.Status.LIVE || stream.getEndedAt() == null
                ? java.time.Instant.now().getEpochSecond()
                : stream.getEndedAt().getEpochSecond();
        return (int) Math.max(1, end - stream.getStartedAt().getEpochSecond());
    }

    /** Critères du lisible-par-tous : PUBLIC + READY + non supprimé. */
    private static List<Specification<Clip>> publicOnly() {
        return List.of(
                (root, query, cb) -> cb.equal(root.get("visibility"), Clip.Visibility.PUBLIC),
                (root, query, cb) -> cb.equal(root.get("processingStatus"), Clip.ProcessingStatus.READY),
                (root, query, cb) -> cb.isFalse(root.get("deleted")));
    }

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

    /** Invalide TOUTES les pages du feed clips en cache (pattern "clips:feed:*"). */
    public void invalidateClipFeeds() {
        cache.evictByPattern(CacheKeys.CLIP_FEED_PREFIX + "*");
    }

    /** Clip visible par cet acteur (PRIVATE/supprimé = 404, existence masquée). */
    private Clip requireVisible(Long id, CurrentUser actor) {
        return requireVisible(findByIdOrThrow(id), actor);
    }

    /** Variante : visibilité d'un clip déjà chargé (lien court §40-41). */
    private Clip requireVisible(Clip clip, CurrentUser actor) {
        if (clip.isDeleted()) {
            throw ApiException.notFound("Clip introuvable (id=" + clip.getId() + ")");
        }
        if (clip.getVisibility() == Clip.Visibility.PRIVATE && !canReadPrivate(clip, actor)) {
            throw ApiException.notFound("Clip introuvable (id=" + clip.getId() + ")");
        }
        return clip;
    }

    private static boolean canReadPrivate(Clip clip, CurrentUser actor) {
        if (actor == null || !actor.isAuthenticated()) {
            return false;
        }
        if (actor.isAdmin() || actor.isModeratorOrAbove()) {
            return true;
        }
        return actor.id().equals(clip.getCreator().getUser().getId());
    }

    private Clip findByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Clip introuvable (id=" + id + ")"));
    }

    private Clip findByShareSlugOrThrow(String shareSlug) {
        return repository.findByShareSlug(shareSlug)
                .orElseThrow(() -> ApiException.notFound("Clip introuvable"));
    }

    private ApiPageResponse<ClipDto> toApi(Page<Clip> page, Pageable pageable) {
        return ApiPageResponse.from(page,
                page.getContent().stream().map(this::toDto).toList());
    }

    private ClipDto toDto(Clip clip) {
        return ClipDto.from(clip);
    }
}
