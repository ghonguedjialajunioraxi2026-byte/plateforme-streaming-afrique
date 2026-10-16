package com.streamingafrique.backend.moderation.dto;

import java.time.Instant;
import java.util.List;

import com.streamingafrique.backend.user.model.User;

/**
 * Fiche modérateur d'un compte (étape 17 : /moderation/users).
 * Profil restreint : JAMAIS d'email (réservé à l'ADMIN) + sanctions.
 */
public record UserSummaryDto(
        Long id,
        String username,
        String displayName,
        User.Role role,
        User.Status status,
        Instant createdAt,
        List<ModerationActionDto> recentActions
) {
    public static UserSummaryDto of(User user, List<ModerationActionDto> actions) {
        return new UserSummaryDto(user.getId(), user.getUsername(),
                user.getDisplayName(), user.getRole(), user.getStatus(),
                user.getCreatedAt(), actions);
    }
}
