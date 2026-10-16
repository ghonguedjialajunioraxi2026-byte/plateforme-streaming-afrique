package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Vérification d'adresse (étape 08) — POST /api/auth/verify-email. Usage unique, 24 h. */
public record VerifyEmailRequest(

        @Schema(description = "Token reçu par email (lien)")
        @NotBlank(message = "Le token est obligatoire")
        String token
) {}
