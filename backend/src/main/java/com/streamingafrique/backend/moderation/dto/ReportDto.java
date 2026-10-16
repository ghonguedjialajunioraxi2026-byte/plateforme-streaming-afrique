package com.streamingafrique.backend.moderation.dto;

import java.time.Instant;

import com.streamingafrique.backend.moderation.model.ModerationReport;

/**
 * Signalement exposé (étape 17). Une seule cible renseignée selon
 * {@code targetType} (les autres sont null) — jamais d'entité JPA.
 */
public record ReportDto(
        Long id,
        Long reporterId,
        ModerationReport.TargetType targetType,
        Long targetUserId,
        Long targetChainId,
        Long targetStreamId,
        Long targetVideoId,
        Long targetClipId,
        Long targetMessageId,
        ModerationReport.Reason reason,
        String description,
        ModerationReport.Status status,
        Instant createdAt
) {
    public static ReportDto from(ModerationReport report) {
        return new ReportDto(report.getId(), report.getReporter().getId(),
                report.getTargetType(),
                report.getTargetUser() != null ? report.getTargetUser().getId() : null,
                report.getTargetChain() != null ? report.getTargetChain().getId() : null,
                report.getTargetStream() != null ? report.getTargetStream().getId() : null,
                report.getTargetVideo() != null ? report.getTargetVideo().getId() : null,
                report.getTargetClip() != null ? report.getTargetClip().getId() : null,
                report.getTargetMessage() != null ? report.getTargetMessage().getId() : null,
                report.getReason(), report.getDescription(), report.getStatus(),
                report.getCreatedAt());
    }
}
