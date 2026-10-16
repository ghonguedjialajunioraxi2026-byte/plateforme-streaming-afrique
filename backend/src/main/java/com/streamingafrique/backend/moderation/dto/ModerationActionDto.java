package com.streamingafrique.backend.moderation.dto;

import java.time.Instant;

import com.streamingafrique.backend.moderation.model.ModerationAction;

/**
 * Sanction exposée (étape 17 : historique, révocation). Jamais de secret.
 */
public record ModerationActionDto(
        Long id,
        Long reportId,
        Long moderatorId,
        Long targetUserId,
        ModerationAction.Action action,
        ModerationAction.Scope scope,
        String reason,
        Instant startsAt,
        Instant endsAt,
        ModerationAction.State state,
        Instant createdAt
) {
    public static ModerationActionDto from(ModerationAction action) {
        return new ModerationActionDto(action.getId(),
                action.getReport() != null ? action.getReport().getId() : null,
                action.getModerator().getId(),
                action.getTargetUser() != null ? action.getTargetUser().getId() : null,
                action.getAction(), action.getScope(), action.getReason(),
                action.getStartsAt(), action.getEndsAt(), action.getState(),
                action.getCreatedAt());
    }
}
