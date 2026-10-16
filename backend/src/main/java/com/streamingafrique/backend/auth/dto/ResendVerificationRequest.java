package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Renvoi du lien de vérification (étape 08) — POST /api/auth/resend-verification.
 * Réponse TOUJOURS générique (anti-énumération), anciens jetons invalidés.
 */
public record ResendVerificationRequest(

        @Schema(description = "Adresse à (re)vérifier", example = "user@example.com")
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        String email
) {}
