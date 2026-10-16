package com.streamingafrique.backend.presence.dto;

import java.time.Instant;
import java.util.List;

/**
 * Liste des utilisateurs en ligne (REST GET /api/presence/online).
 */
public record PresenceOnlineDto(
        long total,
        List<Long> userIds,
        Instant measuredAt
) {
}