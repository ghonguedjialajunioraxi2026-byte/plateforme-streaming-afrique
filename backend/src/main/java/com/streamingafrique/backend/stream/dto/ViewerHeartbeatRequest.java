package com.streamingafrique.backend.stream.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Pulsation de visionnage (étape 10, PARTIE 19) : un viewer ≈ un jeton actif. */
public record ViewerHeartbeatRequest(
        @Schema(description = "Jeton anonyme du viewer (généré côté frontend, sessionStorage)",
                example = "3f9c2a1e-…")
        @NotBlank(message = "Le jeton de visionnage est obligatoire")
        @Size(max = 64, message = "Jeton invalide")
        String viewerToken
) {}
