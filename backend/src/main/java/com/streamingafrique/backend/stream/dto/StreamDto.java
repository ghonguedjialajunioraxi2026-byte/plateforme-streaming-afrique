package com.streamingafrique.backend.stream.dto;

import java.time.Instant;

import com.streamingafrique.backend.stream.model.Stream;

/**
 * DTO de SORTIE — un direct (étape 10 : enrichi lecture + audience).
 *
 * Contenu PUBLIC : métadonnées éditoriales, statut, URL de lecture HLS
 * (si LIVE), compteurs. JAMAIS : empreinte de clé, clé en clair, secret.
 * Statut sérialisé en String.
 */
public record StreamDto(
        Long id,
        String title,
        Long creatorId,
        String creatorSlug,
        Long categoryId,
        String categorySlug,
        String categoryName,
        Stream.Status status,
        String description,
        String thumbnailUrl,
        String language,
        Stream.Visibility visibility,
        String playbackUrl,
        long viewerCount,
        int peakViewers,
        Instant scheduledStartAt,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt
) {}
