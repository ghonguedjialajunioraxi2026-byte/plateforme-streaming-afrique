package com.streamingafrique.backend.stream.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.common.spec.QuerySpecs;
import com.streamingafrique.backend.config.RedisCacheProperties;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.service.StreamEventPublisher;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.config.StreamingProperties;
import com.streamingafrique.backend.stream.dto.StreamDto;
import com.streamingafrique.backend.stream.dto.StreamKeyResponse;
import com.streamingafrique.backend.stream.dto.IngestInfoDto;
import com.streamingafrique.backend.stream.dto.StreamRequest;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.service.MediaProcessingWorker;
import com.streamingafrique.backend.video.service.VideoService;

/**
 * Directs (live).
 *
 * Règles métier :
 *   - cycle de vie STRICT : SCHEDULED → LIVE → ENDED, aucune rétrogradation
 *     possible ;
 *   - un direct LIVE ne peut être ni édité ni supprimé (il se termine puis
 *     on supprime) ;
 *   - la suppression est LOGIQUE (étape 15) : un direct terminé peut porter
 *     une VOD/clips (RESTRICT) — il est masqué des listes, jamais effacé ;
 *   - le "vrai streaming" (flux RTMP/WebRTC) est HORS SCOPE : on modélise
 *     le cycle de vie.
 *
 * Liste : pagination + tri + recherche + filtres sont COMBINÉS en base via
 * {@link Specification} (jamais de chargement en mémoire).
 *
 * CACHE (étape 6.19) :
 *   La page « en direct » (listes par statut) est « très consultée » :
 *   cache-aside avec {@code listsTtl}, invalidée sur chaque écriture
 *   (schedule/start/end/update/delete). Les transitions start/end
 *   évitent AUSSI la statistique {@code stats:streams:live}
 *   (le compteur de directs en cours, voir StatsService, TTL court).
 *   PostgreSQL reste la source de vérité.
 *
 * Autorisation (étape 6.25) : un direct appartient à sa chaîne. Les écritures
 * portent l'ACTEUR authentifié ({@link CurrentUser}) et exigent la propriété
 * de la chaîne OU le rang ADMIN (requireOwnerOrAdmin sur l'id de l'utilisateur
 * propriétaire de la fiche créateur) :
 *   - schedule → le créateur de la chaîne ou un ADMIN peut programmer ;
 *   - start/end/update/delete → même règle (propriétaire ou ADMIN).
 * Lectures : publiques (catatalogue).
 */
@Service
public class StreamService {

    private final StreamRepository repository;
    private final CreatorProfileRepository creatorRepository;
    private final CategoryRepository categoryRepository;
    private final RedisCacheService cache;
    private final ViewerService viewerService;
    private final VideoService videos;
    private final MediaProcessingWorker processing;
    private final StreamEventPublisher events;
    private final StreamKeyService keyService;
    private final StreamingProperties streamingProperties;
    private final Duration listsTtl;

    public StreamService(StreamRepository repository,
                         CreatorProfileRepository creatorRepository,
                         CategoryRepository categoryRepository,
                         RedisCacheService cache,
                         ViewerService viewerService,
                         VideoService videos,
                         MediaProcessingWorker processing,
                         StreamEventPublisher events,
                         StreamKeyService keyService,
                         StreamingProperties streamingProperties,
                         RedisCacheProperties redisProperties) {
        this.repository = repository;
        this.creatorRepository = creatorRepository;
        this.categoryRepository = categoryRepository;
        this.cache = cache;
        this.viewerService = viewerService;
        this.videos = videos;
        this.processing = processing;
        this.events = events;
        this.keyService = keyService;
        this.streamingProperties = streamingProperties;
        this.listsTtl = redisProperties.getListsTtl();
    }

    /** Programme un direct (statut initial : SCHEDULED). */
    @Transactional
    public StreamDto schedule(Long creatorId, StreamRequest request, CurrentUser actor) {
        CreatorProfile creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Fiche créateur introuvable (id=" + creatorId + ")"));
        CurrentUser.requireOwnerOrAdmin(actor, creator.getUser().getId());
        Stream stream = Stream.schedule(creator, request.title().trim(),
                categoryOrNull(request.categoryId()), request.scheduledStartAt());
        StreamDto dto = toDto(repository.save(stream));
        invalidateStreamLists();
        return dto;
    }

    @Transactional(readOnly = true)
    public StreamDto getById(Long id) {
        Stream stream = findByIdOrThrow(id);
        if (stream.isDeleted()) {
            throw ApiException.notFound("Direct introuvable (id=" + id + ")");
        }
        return toDto(stream);
    }

    /** Recherche combinée : search + créateur + catégorie (slug) + statut. */
    @Transactional(readOnly = true)
    public ApiPageResponse<StreamDto> search(String search, Long creatorId,
                                             String categorySlug, Stream.Status status,
                                             Pageable pageable) {
        return search(search, creatorId, categorySlug, status, null, pageable);
    }

    /** Recherche + langue déclarée (étape 19 : découverte par langue). */
    @Transactional(readOnly = true)
    public ApiPageResponse<StreamDto> search(String search, Long creatorId,
                                             String categorySlug, Stream.Status status,
                                             String language, Pageable pageable) {
        List<Specification<Stream>> criteria = new ArrayList<>();
        criteria.add(QuerySpecs.textContains("title", search));
        criteria.add(QuerySpecs.pathEquals("creator.id", creatorId));
        criteria.add(QuerySpecs.relationFieldEquals("category", "slug", categorySlug));
        criteria.add((root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status));
        criteria.add((root, query, cb) -> {
            if (language == null || language.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(cb.lower(root.get("language")),
                    language.trim().toLowerCase(java.util.Locale.ROOT));
        });
        // Supprimés logiques exclus des listes (étape 15 : historique conservé, jamais listé).
        criteria.add((root, query, cb) -> cb.isFalse(root.get("deleted")));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    @Transactional(readOnly = true)
    public ApiPageResponse<StreamDto> getAll(Pageable pageable) {
        return search(null, null, null, null, pageable);
    }

    /** Les directs d'une chaîne. */
    @Transactional(readOnly = true)
    public ApiPageResponse<StreamDto> getByCreator(Long creatorId, Pageable pageable) {
        return search(null, creatorId, null, null, pageable);
    }

    /** Les directs par statut (ex. page "en direct") — cache-aside. */
    @Transactional(readOnly = true)
    public ApiPageResponse<StreamDto> getByStatus(Stream.Status status, Pageable pageable) {
        String key = CacheKeys.streamsList(status == null ? "ALL" : status.name(), pageable);

        Object cached = cache.getJson(key);
        if (cached instanceof ApiPageResponse<?> page) {
            @SuppressWarnings("unchecked")
            ApiPageResponse<StreamDto> result = (ApiPageResponse<StreamDto>) page;
            return result;
        }

        ApiPageResponse<StreamDto> fromDb = search(null, null, null, status, pageable);
        cache.putJson(key, fromDb, listsTtl);
        return fromDb;
    }

    /** Les directs d'une catégorie (alias historique : ?categoryId=N). */
    @Transactional(readOnly = true)
    public ApiPageResponse<StreamDto> getByCategory(Long categoryId, Pageable pageable) {
        List<Specification<Stream>> criteria = new ArrayList<>();
        criteria.add(QuerySpecs.pathEquals("category.id", categoryId));
        criteria.add((root, query, cb) -> cb.isFalse(root.get("deleted")));
        return toApi(repository.findAll(QuerySpecs.and(criteria), pageable), pageable);
    }

    /** Transition SCHEDULED → LIVE. */
    @Transactional
    public StreamDto start(Long id, CurrentUser actor) {        Stream stream = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, stream.getCreator().getUser().getId());
        if (stream.getStatus() != Stream.Status.SCHEDULED) {
            throw ApiException.badRequest("Impossible de démarrer un direct au statut " + stream.getStatus());
        }
        stream.markStarted();
        StreamDto dto = toDto(repository.save(stream));
        invalidateStreamLists();
        invalidateLiveCount();
        return dto;
    }

    /**
     * Émet (ou régénère) la stream key — FINALISATION.
     * Propriétaire de la chaîne ou ADMIN. La clé en clair n'est montrée
     * QU'UNE fois (jamais loggée, jamais ré-exposable : seule l'empreinte
     * SHA-256 persiste). Régénérer invalide l'ancienne clé.
     */
    @Transactional
    public StreamKeyResponse issueStreamKey(Long id, CurrentUser actor) {
        Stream stream = findByIdOrThrow(id);
        if (stream.isDeleted()) {
            throw ApiException.notFound("Direct introuvable (id=" + id + ")");
        }
        CurrentUser.requireOwnerOrAdmin(actor, stream.getCreator().getUser().getId());
        String plain = keyService.generate();
        String playback = stream.getPlaybackUrl() != null ? stream.getPlaybackUrl()
                : streamingProperties.getHlsBaseUrl() + "/live/" + stream.getId() + ".m3u8";
        stream.attachIngest(keyService.hash(plain), Stream.IngestProtocol.RTMP, playback);
        repository.save(stream);
        invalidateStreamLists();
        return new StreamKeyResponse(id, streamingProperties.getRtmpUrl(), plain,
                keyService.obsKey(id, plain));
    }

    /**
     * Infos d'ingest pour le propriétaire (page « aide OBS ») — FINALISATION.
     * URLs + présence d'une clé, JAMAIS la clé elle-même.
     */
    @Transactional(readOnly = true)
    public IngestInfoDto ingestInfo(Long id, CurrentUser actor) {
        Stream stream = findByIdOrThrow(id);
        if (stream.isDeleted()) {
            throw ApiException.notFound("Direct introuvable (id=" + id + ")");
        }
        CurrentUser.requireOwnerOrAdmin(actor, stream.getCreator().getUser().getId());
        String hash = stream.getStreamKeyHash();
        return new IngestInfoDto(id, streamingProperties.getRtmpUrl(), stream.getPlaybackUrl(),
                stream.getIngestProtocol() != null ? stream.getIngestProtocol().name() : "RTMP",
                hash != null && !hash.isBlank());
    }

    /** Transition LIVE → ENDED (+ VOD auto + événement, étape 15 §13). */
    @Transactional
    public StreamDto end(Long id, CurrentUser actor) {
        Stream stream = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, stream.getCreator().getUser().getId());
        if (stream.getStatus() != Stream.Status.LIVE) {
            throw ApiException.badRequest("Seul un direct en cours peut être terminé (statut : " + stream.getStatus() + ")");
        }
        stream.markEnded();
        StreamDto dto = toDto(repository.save(stream));
        invalidateStreamLists();
        invalidateLiveCount();
        // Fin de live → VOD automatique (PROCESSING, worker async) + diffusion temps réel.
        // Réutilise le hook existant : aucune mécanique de fin dupliquée (§6, §13).
        events.publish(EventType.STREAM_ENDED,
                java.util.Map.of("streamId", stream.getId(),
                        "creatorId", stream.getCreator().getId()));
        Video vod = videos.createFromStreamEnd(stream);
        processing.processVideoAsync(vod.getId());
        return dto;
    }

    /** Édit du direct entièrement autorisé (programmable / terminé), JAMAIS pour un LIVE. */
    @Transactional
    public StreamDto update(Long id, StreamRequest request, CurrentUser actor) {
        Stream stream = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, stream.getCreator().getUser().getId());
        if (stream.getStatus() == Stream.Status.LIVE) {
            throw ApiException.conflict("Un direct en cours ne peut pas être modifié");
        }
        stream.updateSchedule(request.title().trim(),
                categoryOrNull(request.categoryId()), request.scheduledStartAt());
        StreamDto dto = toDto(repository.save(stream));
        invalidateStreamLists();
        return dto;
    }

    /** Suppression LOGIQUE (étape 15, §25) : masqué des listes (détail = 404
     *  via le contrôleur) mais conservé — une VOD/clip peut référencer ce
     *  direct (RESTRICT + historique). Interdite tant que le direct est en cours. */
    @Transactional
    public void delete(Long id, CurrentUser actor) {
        Stream stream = findByIdOrThrow(id);
        CurrentUser.requireOwnerOrAdmin(actor, stream.getCreator().getUser().getId());
        if (stream.getStatus() == Stream.Status.LIVE) {
            throw ApiException.conflict("Terminez d'abord le direct avant de le supprimer");
        }
        stream.markDeleted();
        repository.save(stream);
        invalidateStreamLists();
    }

    /* ─── Cache des listes de directs ────────────────────────── */

    /** Invalide TOUTES les listes de directs en cache (pattern "streams:list:*"). */
    private void invalidateStreamLists() {
        cache.evictByPattern(CacheKeys.STREAM_LIST_PREFIX + "*");
    }

    /** Invalide le compteur de directs en cours (transition start/end). */
    private void invalidateLiveCount() {
        cache.evict(CacheKeys.liveStreamsCount());
    }

    /** Résout la catégorie optionnelle (null si non fournie). */
    private Category categoryOrNull(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Catégorie introuvable (id=" + categoryId + ")"));
    }

    private Stream findByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Direct introuvable (id=" + id + ")"));
    }

    private ApiPageResponse<StreamDto> toApi(Page<Stream> page, Pageable pageable) {
        return ApiPageResponse.from(page,
                page.getContent().stream().map(this::toDto).toList());
    }

    private StreamDto toDto(Stream stream) {
        CreatorProfile creator = stream.getCreator();
        Category category = stream.getCategory();
        return new StreamDto(stream.getId(), stream.getTitle(),
                creator != null ? creator.getId() : null,
                creator != null ? creator.getSlug() : null,
                category != null ? category.getId() : null,
                category != null ? category.getSlug() : null,
                category != null ? category.getName() : null,
                stream.getStatus(), stream.getDescription(),
                stream.getThumbnailUrl(), stream.getLanguage(),
                stream.getVisibility(), stream.getPlaybackUrl(),
                stream.getId() != null ? viewerService.currentViewers(stream.getId()) : 0L,
                stream.getPeakViewers(),
                stream.getScheduledStartAt(), stream.getStartedAt(),
                stream.getEndedAt(), stream.getCreatedAt());
    }
}