package com.streamingafrique.backend.profile.dto;

import java.time.Instant;

/**
 * DTO de SORTIE — profil PUBLIC d'un utilisateur (étape 09).
 *
 * Liste blanche stricte : AUCUN email, AUCUNE naissance, AUCUNE préférence,
 * AUCUN secret. Servi uniquement si le profil est public ({@code isPublic}),
 * sinon 404 (comme un compte privé).
 */
public record PublicProfileDto(
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        String countryCode,
        String language,
        String websiteUrl,
        String xHandle,
        String instagramHandle,
        String youtubeUrl,
        Instant createdAt
) {}
