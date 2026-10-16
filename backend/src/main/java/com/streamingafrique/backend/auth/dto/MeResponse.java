package com.streamingafrique.backend.auth.dto;

import com.streamingafrique.backend.user.model.User;

/**
 * Utilisateur connecté (étape 08) — GET /api/auth/me.
 *
 * Sous-ensemble STRICT du compte : jamais de passwordHash, jamais de token,
 * jamais de secret ni d'information interne. Le frontend y lit user, rôle
 * et vérification email pour ses guards.
 */
public record MeResponse(
        Long id,
        String username,
        String email,
        String displayName,
        User.Role role,
        boolean emailVerified
) {
    public static MeResponse of(User user) {
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), user.getRole(), user.isEmailVerified());
    }
}
