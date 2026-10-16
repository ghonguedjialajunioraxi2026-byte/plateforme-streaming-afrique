package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Mot de passe oublié (étape 08) — POST /api/auth/forgot-password.
 * Réponse TOUJOURS générique, compte existant ou non (anti-énumération).
 */
public record ForgotPasswordRequest(

        @Schema(description = "Adresse du compte à récupérer", example = "user@example.com")
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        String email
) {}
