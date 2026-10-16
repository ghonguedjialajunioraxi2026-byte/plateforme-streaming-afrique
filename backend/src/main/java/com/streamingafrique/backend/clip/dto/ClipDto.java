package com.streamingafrique.backend.clip.dto;

import java.time.Instant;

import com.streamingafrique.backend.clip.model.Clip;

/**
 * DTO de SORTIE — un clip (étape 15 : + partage, auteur, vues, segment,
 * traitement). Une seule source : streamId OU videoId. Jamais l'entité JPA.
 */
public record ClipDto(
        Long id,
        String title,
        Long creatorId,
        Long streamId,
        Long videoId,
        String clipUrl,
        String thumbnailUrl,
        int durationSeconds,
        Clip.Visibility visibility,
        Instant createdAt,
        /** Lien court de partage (/clip/{id} + slug copiable). */
        String shareSlug,
        /** Compte ayant découpé (NULL = historique chaîne). */
        Long createdByUserId,
        /** Vues cumulées (compteur persisté, anti-abus côté écriture). */
        long viewCount,
        /** Position de l'extrait dans la source (secondes). */
        int startsAtSeconds,
        /** État du fichier (READY = lisible). */
        Clip.ProcessingStatus processingStatus,
        /** Masqué logiquement (détail public = 404, supervisé par l'admin). */
        boolean deleted
) {
    public static ClipDto from(Clip clip) {
        return new ClipDto(clip.getId(), clip.getTitle(), clip.getCreator().getId(),
                clip.getStream() != null ? clip.getStream().getId() : null,
                clip.getVideo() != null ? clip.getVideo().getId() : null,
                clip.getClipUrl(), clip.getThumbnailUrl(), clip.getDurationSeconds(),
                clip.getVisibility(), clip.getCreatedAt(), clip.getShareSlug(),
                clip.getCreatedBy() != null ? clip.getCreatedBy().getId() : null,
                clip.getViewCount(), clip.getStartsAtSeconds(),
                clip.getProcessingStatus(), clip.isDeleted());
    }
}
