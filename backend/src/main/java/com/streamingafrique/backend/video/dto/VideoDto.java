package com.streamingafrique.backend.video.dto;

import java.time.Instant;

import com.streamingafrique.backend.video.model.Video;

/**
 * DTO de SORTIE — une vidéo VOD (étape 15 : + vues, traitement, origine).
 * Jamais l'entité JPA.
 */
public record VideoDto(
        Long id,
        String title,
        String description,
        Long creatorId,
        Long categoryId,
        String videoUrl,
        String thumbnailUrl,
        int durationSeconds,
        Video.Visibility visibility,
        Instant publishedAt,
        Instant createdAt,
        /** Vues cumulées (compteur persisté, anti-abus côté écriture). */
        long viewCount,
        /** État du fichier (READY = lisible, PROCESSING/FAILED sinon). */
        Video.ProcessingStatus processingStatus,
        /** Direct d'origine (replay auto) — NULL = téléversement direct. */
        Long originStreamId,
        /** Masquée logiquement (détail public = 404, supervisée par l'admin). */
        boolean deleted,
        /** Langue déclarée du contenu (NULL = non renseignée). */
        String language
) {
    public static VideoDto from(Video video) {
        return new VideoDto(video.getId(), video.getTitle(), video.getDescription(),
                video.getCreator().getId(),
                video.getCategory() != null ? video.getCategory().getId() : null,
                video.getVideoUrl(), video.getThumbnailUrl(), video.getDurationSeconds(),
                video.getVisibility(), video.getPublishedAt(), video.getCreatedAt(),
                video.getViewCount(), video.getProcessingStatus(),
                video.getOriginStream() != null ? video.getOriginStream().getId() : null,
                video.isDeleted(), video.getLanguage());
    }
}
