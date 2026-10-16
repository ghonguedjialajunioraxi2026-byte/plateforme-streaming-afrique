package com.streamingafrique.backend.creator.dashboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Modération de l'espace du créateur (étape 16 : {@code GET /api/creator/moderation}).
 *
 * Signalements visant DIRECTEMENT la chaîne (TargetType.CHAIN). Le créateur ne
 * voit que son périmètre — jamais la file globale des modérateurs. Les actions
 * (ban, timeout…) restent effectuées via les outils existants selon les rôles.
 */
public record CreatorModerationDto(
        long openReports,
        List<ReportInfo> recentOpenReports
) {
    /** Un signalement ouvert contre la chaîne (vue créateur). */
    public record ReportInfo(
            Long id,
            String reason,
            String description,
            String status,
            Instant createdAt
    ) {
    }
}
