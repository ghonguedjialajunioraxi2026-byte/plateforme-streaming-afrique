package com.streamingafrique.backend.clip.dto;

import com.streamingafrique.backend.clip.model.Clip;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** DTO d'ENTRÉE — changement de visibilité d'un clip. */
public record ClipVisibilityRequest(
        @Schema(description = "Visibilité (le feed public ne liste que PUBLIC)",
                example = "PRIVATE")
        @NotNull(message = "La visibilité est obligatoire")
        Clip.Visibility visibility
) {}
