package com.streamingafrique.backend.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Masquage d'un message de chat (étape 17 : motif obligatoire, audité). */
public record HideMessageRequest(
        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
        String reason
) {
}
