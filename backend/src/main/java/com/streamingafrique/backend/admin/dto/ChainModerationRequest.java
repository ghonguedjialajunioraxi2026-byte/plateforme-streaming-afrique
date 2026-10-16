package com.streamingafrique.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motif d'un acte sur une chaîne (suspension, réactivation). */
public record ChainModerationRequest(
        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
        String reason
) {
}
