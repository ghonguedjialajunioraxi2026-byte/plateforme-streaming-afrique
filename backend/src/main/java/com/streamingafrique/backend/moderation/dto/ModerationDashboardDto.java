package com.streamingafrique.backend.moderation.dto;

import com.streamingafrique.backend.dto.ApiPageResponse;

/** Synthèse du centre de modération (jamais d'entité). */
public record ModerationDashboardDto(
        long openReports,
        long activeSanctions,
        ApiPageResponse<ReportDto> recentOpen
) {
}
