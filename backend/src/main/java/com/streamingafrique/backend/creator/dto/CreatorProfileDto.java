package com.streamingafrique.backend.creator.dto;

import java.time.Instant;

import com.streamingafrique.backend.creator.model.CreatorProfile;

/**
 * DTO de SORTIE — fiche publique d'un créateur (chaîne), étape 09.
 *
 * Champs historiques conservés en tête (compatibilité), enrichissements en
 * fin : abonnés (calculé, jamais stocké), catégorie principale, statut.
 * Aucun secret, aucun email du propriétaire (l'id technique suffit aux
 * recoupements autorisés côté backend).
 */
public record CreatorProfileDto(
        Long id,
        String displayName,
        String slug,
        String bio,
        String avatarUrl,
        String bannerUrl,
        Long userId,
        Instant createdAt,
        long followerCount,
        CategoryRef primaryCategory,
        CreatorProfile.Status status
) {
    /** Référence légère de catégorie (jamais l'entité). */
    public record CategoryRef(
            Long id,
            String slug,
            String name
    ) {}
}
