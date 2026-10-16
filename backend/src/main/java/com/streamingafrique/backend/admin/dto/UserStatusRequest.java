package com.streamingafrique.backend.admin.dto;

import com.streamingafrique.backend.user.model.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Changement de statut d'un compte (étape 17 : suspension, réactivation,
 * fermeture). Le motif est OBLIGATOIRE (dossier d'audit).
 */
public record UserStatusRequest(
        @NotNull(message = "Le statut est obligatoire")
        User.Status status,

        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
        String reason
) {
}
