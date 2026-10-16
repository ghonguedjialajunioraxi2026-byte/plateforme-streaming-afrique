package com.streamingafrique.backend.stream.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE d'un direct (programmation / modification).
 * categoryId et scheduledStartAt restent optionnels.
 */
public record StreamRequest(

        @Schema(description = "Titre du direct", example = "Tournoi FIFA")
        @NotBlank(message = "Le titre du direct est obligatoire")
        @Size(max = 120, message = "Le titre ne doit pas dépasser 120 caractères")
        String title,

        @Schema(description = "Catégorie (optionnel)", example = "1")
        Long categoryId,

        @Schema(description = "Début programmé ISO-8601 UTC (optionnel, défaut : maintenant)",
                example = "2026-10-01T20:00:00Z")
        Instant scheduledStartAt
) {}