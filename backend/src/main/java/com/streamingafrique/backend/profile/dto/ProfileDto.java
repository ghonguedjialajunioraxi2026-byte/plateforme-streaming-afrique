package com.streamingafrique.backend.profile.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO de SORTIE — profil COMPLET du compte connecté (étape 09).
 *
 * Réservé au propriétaire (+ email, naissance, préférences) : ne JAMAIS
 * renvoyer ce DTO sur une route publique (voir {@link PublicProfileDto}).
 * Aucun secret : ni passwordHash, ni token, ni donnée interne.
 */
public record ProfileDto(
        Long userId,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        boolean emailVerified,
        String bio,
        String countryCode,
        String language,
        String timezone,
        String preferredCurrency,
        String websiteUrl,
        String xHandle,
        String instagramHandle,
        String youtubeUrl,
        LocalDate birthDate,
        boolean notifyLive,
        boolean notifyContent,
        boolean isPublic,
        Instant createdAt
) {}
