package com.streamingafrique.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Créer ou améliorer la description d'un live / d'une chaîne. */
public record DescriptionRequest(
        @NotBlank @Size(max = 2000) String text,
        @Size(max = 30) String mode,
        @Size(max = 5) String language) {
}
