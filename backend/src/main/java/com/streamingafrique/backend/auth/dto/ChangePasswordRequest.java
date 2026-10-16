package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Changement connecté (étape 08) — POST /api/auth/change-password (authentifié). */
public record ChangePasswordRequest(

        @Schema(description = "Mot de passe actuel (vérifié avant toute modification)")
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,

        @Schema(description = "Nouveau mot de passe (min 8, lettre + chiffre requis)")
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
        String newPassword,

        @Schema(description = "Confirmation (doit être identique)")
        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        String confirmPassword
) {}
