package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Réinitialisation (étape 08) — POST /api/auth/reset-password. Token 1 h, usage unique. */
public record ResetPasswordRequest(

        @Schema(description = "Token reçu par email (lien)")
        @NotBlank(message = "Le token est obligatoire")
        String token,

        @Schema(description = "Nouveau mot de passe (min 8, lettre + chiffre requis)")
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
        String newPassword,

        @Schema(description = "Confirmation (doit être identique)")
        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        String confirmPassword
) {}
