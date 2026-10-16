package com.streamingafrique.backend.auth.dto;

import com.streamingafrique.backend.user.model.User;

/**
 * Réponse de connexion réussie (étapes 6.24 + 08).
 *
 * Champs 6.24 inchangés (compatibilité) + paire refresh de l'étape 08 :
 * @param accessToken     jeton d'accès à présenter dans {@code Authorization: Bearer <token>}
 * @param tokenType       schéma de transmission du jeton (toujours "Bearer")
 * @param expiresIn       durée de validité restante du jeton, en secondes
 * @param userId          identifiant du compte
 * @param email           identifiant de connexion (subject du jeton)
 * @param displayName     nom affiché publiquement
 * @param role            rôle du compte (les autorisations découlent de ce rôle)
 * @param refreshToken    jeton de rafraîchissement (7 j, rotation à chaque usage)
 * @param refreshExpiresIn durée de validité restante du refresh, en secondes
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String email,
        String displayName,
        User.Role role,
        String refreshToken,
        long refreshExpiresIn
) {}