package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Renouvellement (étape 08) — POST /api/auth/refresh. Rotation systématique. */
public record RefreshRequest(

        @Schema(description = "Refresh token remis au login/refresh précédent")
        @NotBlank(message = "Le refresh token est obligatoire")
        String refreshToken
) {}
