package com.streamingafrique.backend.admin.dto;

import java.time.Instant;

import com.streamingafrique.backend.user.model.User;

/**
 * Compte exposé à l'administration (étape 17).
 *
 * Jamais de secret : ni hash, ni jeton, ni clé. L'email reste visible aux
 * ADMIN (nécessaire au support) mais ne sort jamais vers d'autres rôles.
 */
public record AdminUserDto(
        Long id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        User.Role role,
        User.Status status,
        Instant lastLoginAt,
        boolean emailVerified,
        Instant createdAt
) {
    public static AdminUserDto from(User user) {
        return new AdminUserDto(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), user.getAvatarUrl(), user.getRole(), user.getStatus(),
                user.getLastLoginAt(), user.isEmailVerified(), user.getCreatedAt());
    }
}
