package com.streamingafrique.backend.admin.dto;

import java.time.Instant;

import com.streamingafrique.backend.admin.model.AdminAuditLog;

/**
 * Acte d'audit exposé (étape 17 : /admin/logs). Lecture seule par nature
 * (append-only) : aucun secret n'y figure jamais (ni JWT, ni hash, ni clé).
 */
public record AuditLogDto(
        Long id,
        Long actorId,
        String actorUsername,
        String action,
        String targetType,
        Long targetId,
        String beforeJson,
        String afterJson,
        String reason,
        Instant createdAt
) {
    public static AuditLogDto from(AdminAuditLog log) {
        return new AuditLogDto(log.getId(), log.getActor().getId(),
                log.getActor().getUsername(), log.getAction(), log.getTargetType(),
                log.getTargetId(), log.getBeforeJson(), log.getAfterJson(),
                log.getReason(), log.getCreatedAt());
    }
}
