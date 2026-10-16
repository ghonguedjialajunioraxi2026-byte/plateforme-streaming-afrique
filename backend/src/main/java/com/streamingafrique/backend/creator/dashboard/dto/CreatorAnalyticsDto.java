package com.streamingafrique.backend.creator.dashboard.dto;

import java.time.Instant;
import java.util.List;

import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.video.dto.VideoDto;

/**
 * Analytique de la chaîne (étape 16 : {@code GET /api/creator/analytics?days=N}).
 *
 * Totaux calculés en direct depuis PostgreSQL (agrégations SQL) ; la série
 * temporelle provient des snapshots {@code CHAIN} quand le job de consolidation
 * les a écrits — tableau vide (jamais de données inventées) sinon. Les filtres
 * {@code Aujourd'hui / 7 / 30 / 90 jours} sont portés par {@code days} (1..90).
 */
public record CreatorAnalyticsDto(
        int periodDays,
        Instant from,
        Instant to,
        long videoViews,
        long clipViews,
        long followers,
        long subscribers,
        long lives,
        long giftsReceived,
        long giftCoinsEarned,
        List<SeriesPoint> series,
        List<VideoDto> topVideos,
        List<ClipDto> topClips
) {
    /** Un point de courbe (métrique du snapshot, jamais synthétisé). */
    public record SeriesPoint(
            String metric,
            Instant periodStart,
            long value
    ) {
    }
}
