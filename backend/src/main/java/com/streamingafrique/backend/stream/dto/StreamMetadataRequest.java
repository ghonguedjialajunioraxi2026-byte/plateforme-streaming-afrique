package com.streamingafrique.backend.stream.dto;

import com.streamingafrique.backend.stream.model.Stream;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE — métadonnées éditoriales d'un direct (étape 10, PARTIE 5).
 *
 * Tous champs OPTIONNELS (null = inchangé). AUCUN champ système
 * (owner, status, compteurs, dates) : la protection anti mass-assignment
 * est structurelle — ces champs n'existent pas ici.
 */
public record StreamMetadataRequest(

        @Schema(description = "Description du direct (2000 max, vide = retrait)", example = "Tournoi FIFA avec la commu")
        @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères")
        String description,

        @Schema(description = "Miniature (URL http(s) ou /uploads/, vide = retrait)",
                example = "https://cdn.example.com/thumb.jpg")
        @Size(max = 500, message = "L'URL de miniature ne doit pas dépasser 500 caractères")
        String thumbnailUrl,

        @Schema(description = "Langue du direct (ex. fr, wo)", example = "fr")
        @Size(max = 10, message = "La langue ne doit pas dépasser 10 caractères")
        String language,

        @Schema(description = "Visibilité éditoriale (null = inchangée)", example = "PUBLIC")
        Stream.Visibility visibility
) {}
