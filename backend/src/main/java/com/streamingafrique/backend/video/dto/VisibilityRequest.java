package com.streamingafrique.backend.video.dto;

import com.streamingafrique.backend.video.model.Video;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** DTO d'ENTRÉE — changement de visibilité d'une vidéo. */
public record VisibilityRequest(
        @Schema(description = "Visibilité (le feed public ne liste que PUBLIC)",
                example = "PRIVATE")
        @NotNull(message = "La visibilité est obligatoire")
        Video.Visibility visibility
) {}