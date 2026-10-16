package com.streamingafrique.backend.video.dto;

import jakarta.validation.constraints.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE — langue déclarée d'une VOD (étape 19). NULL = non
 * renseignée (jamais bloquant, jamais devinée automatiquement).
 */
public record LanguageRequest(
        @Schema(description = "Langue du contenu (2-3 lettres) ou null", example = "fr")
        @Pattern(regexp = "[A-Za-z]{2,3}", message = "Langue invalide (2-3 lettres)")
        String language
) {}
