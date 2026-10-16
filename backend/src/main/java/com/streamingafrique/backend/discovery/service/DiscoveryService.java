package com.streamingafrique.backend.discovery.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.discovery.dto.TrendingResponse;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.profile.model.UserProfile;
import com.streamingafrique.backend.profile.repository.UserProfileRepository;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.region.service.RegionService;
import com.streamingafrique.backend.stream.dto.StreamDto;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Découverte Africa+ (étape 19) — tendances régionales + émergents.
 *
 * Score transparent (pas de biais caché) :
 * <pre>
 *   live    = 100 (LIVE) + 2 × peakViewers + récence (≤48 h : +50 → 0)
 *   vidéo   = 2 × ln(1 + vues) × 10 + récence (≤30 j : +30 → 0)
 *   émergent = follows gagnés sur 30 j (≥ 2), chaîne ACTIVE
 * </pre>
 * Filtres {@code country} (pays déclaré du créateur — pas de GPS) et
 * {@code language} (langue déclarée du contenu). Résultats cachés 2 min.
 */
@Service
public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final StreamRepository streams;
    private final VideoRepository videos;
    private final FollowRepository follows;
    private final CreatorProfileRepository channels;
    private final UserProfileRepository profiles;
    private final RegionService regions;
    private final RedisCacheService cache;
    private final ObjectMapper mapper = new ObjectMapper();

    public DiscoveryService(StreamRepository streams, VideoRepository videos,
                            FollowRepository follows,
                            CreatorProfileRepository channels,
                            UserProfileRepository profiles,
                            RegionService regions, RedisCacheService cache) {
        this.streams = streams;
        this.videos = videos;
        this.follows = follows;
        this.channels = channels;
        this.profiles = profiles;
        this.regions = regions;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public TrendingResponse trending(String country, String language,
                                     String categorySlug, int size) {
        // Code inconnu → 404 explicite (jamais de filtre silencieux).
        if (country != null && !country.isBlank()) {
            regions.country(country);
        }
        int limit = Math.min(Math.max(size, 1), 20);
        String cacheKey = "discovery:trending:"
                + normOpt(country) + ":" + normOpt(language) + ":"
                + normOpt(categorySlug) + ":" + limit;
        try {
            String cached = cache.get(cacheKey);
            if (cached != null) {
                return mapper.readValue(cached,
                        new TypeReference<TrendingResponse>() {
                        });
            }
        } catch (Exception ex) {
            log.debug("Cache découverte illisible : {}", ex.getMessage());
        }
        TrendingResponse response = new TrendingResponse(
                trendingStreams(country, language, categorySlug, limit),
                trendingVideos(country, language, categorySlug, limit),
                emergingCreators(country, 5));
        try {
            cache.put(cacheKey, mapper.writeValueAsString(response),
                    Duration.ofMinutes(2));
        } catch (Exception ex) {
            log.debug("Cache découverte inscriptible : {}", ex.getMessage());
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<CreatorProfileDto> emerging(String country, int size) {
        return emergingCreators(country, Math.min(Math.max(size, 1), 20));
    }

    /* ─── Tendances ─────────────────────────────────────────────── */

    private List<TrendingResponse.ScoredStream> trendingStreams(
            String country, String language, String categorySlug, int limit) {
        List<Specification<Stream>> criteria = new ArrayList<>();
        criteria.add((root, query, cb) -> cb.isFalse(root.get("deleted")));
        if (categorySlug != null && !categorySlug.isBlank()) {
            criteria.add((root, query, cb) -> cb.equal(
                    root.get("category").get("slug"), categorySlug.trim()));
        }
        if (language != null && !language.isBlank()) {
            criteria.add((root, query, cb) -> cb.equal(
                    cb.lower(root.get("language")), language.trim()
                            .toLowerCase(Locale.ROOT)));
        }
        if (country != null && !country.isBlank()) {
            criteria.add(creatorCountryIs(country, "creator"));
        }
        Specification<Stream> spec = criteria.stream()
                .reduce(Specification::and).orElse(null);
        // Candidats : les plus récents d'abord (la récence fait partie du
        // score final calculé sur la page — pas de N+1, pas de table complète).
        List<Stream> candidates = streams.findAll(spec,
                PageRequest.of(0, 60)).getContent();
        Instant now = Instant.now();
        return candidates.stream()
                .map(s -> new TrendingResponse.ScoredStream(toStreamDto(s),
                        liveScore(s, now)))
                .sorted(Comparator.comparingDouble(
                        TrendingResponse.ScoredStream::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<TrendingResponse.ScoredVideo> trendingVideos(
            String country, String language, String categorySlug, int limit) {
        List<Specification<Video>> criteria = new ArrayList<>();
        criteria.add((root, query, cb) -> cb.isFalse(root.get("deleted")));
        criteria.add((root, query, cb) -> cb.equal(root.get("visibility"),
                Video.Visibility.PUBLIC));
        if (categorySlug != null && !categorySlug.isBlank()) {
            criteria.add((root, query, cb) -> cb.equal(
                    root.get("category").get("slug"), categorySlug.trim()));
        }
        if (language != null && !language.isBlank()) {
            criteria.add((root, query, cb) -> cb.equal(
                    cb.lower(root.get("language")), language.trim()
                            .toLowerCase(Locale.ROOT)));
        }
        if (country != null && !country.isBlank()) {
            criteria.add(creatorCountryIs(country, "creator"));
        }
        Specification<Video> spec = criteria.stream()
                .reduce(Specification::and).orElse(null);
        List<Video> candidates = videos.findAll(spec,
                PageRequest.of(0, 60)).getContent();
        Instant now = Instant.now();
        return candidates.stream()
                .map(v -> new TrendingResponse.ScoredVideo(
                        VideoDto.from(v), videoScore(v, now)))
                .sorted(Comparator.comparingDouble(
                        TrendingResponse.ScoredVideo::score).reversed())
                .limit(limit)
                .toList();
    }

    /** Score live : statut + audience + récence (heures depuis le début). */
    double liveScore(Stream stream, Instant now) {
        double score = stream.getStatus() == Stream.Status.LIVE ? 100.0 : 0.0;
        score += 2.0 * stream.getPeakViewers();
        Instant ref = stream.getStartedAt() != null ? stream.getStartedAt()
                : stream.getCreatedAt();
        if (ref != null) {
            long hours = Duration.between(ref, now).toHours();
            score += Math.max(0.0, 50.0 - hours);
        }
        return score;
    }

    /** Score vidéo : audience (log) + récence (jours depuis publication). */
    double videoScore(Video video, Instant now) {
        double score = 20.0 * Math.log1p(Math.max(0, video.getViewCount()));
        Instant ref = video.getPublishedAt() != null ? video.getPublishedAt()
                : video.getCreatedAt();
        if (ref != null) {
            long days = Duration.between(ref, now).toDays();
            score += Math.max(0.0, 30.0 - days);
        }
        return score;
    }

    /**
     * Le créateur a déclaré {@code country} dans son profil (pays déclaré,
     * jamais de géolocalisation). Sous-requête EXISTS — pas de N+1.
     */
    private <T> Specification<T> creatorCountryIs(String country,
                                                  String creatorPath) {
        String norm = country.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<UserProfile> profile = sub.from(UserProfile.class);
            sub.select(cb.literal(1L));
            sub.where(
                    cb.equal(profile.get("user").get("id"),
                            root.get(creatorPath).get("user").get("id")),
                    cb.equal(cb.upper(profile.get("countryCode")), norm));
            return cb.exists(sub);
        };
    }

    /* ─── Émergents ─────────────────────────────────────────────── */

    private List<CreatorProfileDto> emergingCreators(String country, int limit) {
        Instant since = Instant.now().minus(Duration.ofDays(30));
        List<Object[]> rows = follows.countRecentFollowsByChannel(since);
        List<CreatorProfileDto> emerging = new ArrayList<>();
        String normCountry = country == null || country.isBlank() ? null
                : country.trim().toUpperCase(Locale.ROOT);
        for (Object[] row : rows) {
            if (emerging.size() >= limit) {
                break;
            }
            Long channelId = (Long) row[0];
            long recentFollows = (long) row[1];
            if (recentFollows < 2) {
                continue;
            }
            channels.findById(channelId).ifPresent(channel -> {
                if (channel.getStatus() != CreatorProfile.Status.ACTIVE) {
                    return;
                }
                if (normCountry != null && !creatorInCountry(channel,
                        normCountry)) {
                    return;
                }
                emerging.add(toCreatorDto(channel));
            });
        }
        return emerging;
    }

    private boolean creatorInCountry(CreatorProfile channel, String normCountry) {
        if (channel.getUser() == null || channel.getUser().getId() == null) {
            return false;
        }
        // Lecture ciblée (petit volume : émergents ≤ 20, jamais de N+1 large).
        return profiles.findByUserId(channel.getUser().getId())
                .map(p -> normCountry.equalsIgnoreCase(
                        p.getCountryCode() == null ? "" : p.getCountryCode()))
                .orElse(false);
    }

    private CreatorProfileDto toCreatorDto(CreatorProfile channel) {
        CreatorProfileDto.CategoryRef category =
                channel.getPrimaryCategory() == null ? null
                        : new CreatorProfileDto.CategoryRef(
                                channel.getPrimaryCategory().getId(),
                                channel.getPrimaryCategory().getSlug(),
                                channel.getPrimaryCategory().getName());
        return new CreatorProfileDto(channel.getId(),
                channel.getDisplayName(), channel.getSlug(), channel.getBio(),
                channel.getAvatarUrl(), channel.getBannerUrl(),
                channel.getUser() == null ? null : channel.getUser().getId(),
                channel.getCreatedAt(),
                follows.countByFollowedId(channel.getId()),
                category, channel.getStatus());
    }

    private StreamDto toStreamDto(Stream stream) {
        CreatorProfile creator = stream.getCreator();
        return new StreamDto(stream.getId(), stream.getTitle(),
                creator == null ? null : creator.getId(),
                creator == null ? null : creator.getSlug(),
                stream.getCategory() == null ? null
                        : stream.getCategory().getId(),
                stream.getCategory() == null ? null
                        : stream.getCategory().getSlug(),
                stream.getCategory() == null ? null
                        : stream.getCategory().getName(),
                stream.getStatus(), stream.getDescription(),
                stream.getThumbnailUrl(), stream.getLanguage(),
                stream.getVisibility(), stream.getPlaybackUrl(), 0,
                stream.getPeakViewers(), stream.getScheduledStartAt(),
                stream.getStartedAt(), stream.getEndedAt(),
                stream.getCreatedAt());
    }

    private String normOpt(String value) {
        return value == null || value.isBlank() ? "-"
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
