package com.streamingafrique.backend.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE d'une vidéo VOD. La visibilité par défaut (PUBLIC) est
 * décidée par le service.
 */
public record VideoRequest(

        @Schema(description = "Titre de la vidéo", example = "Tuto JPA")
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
        String title,

        @Schema(description = "Description (optionnel)", example = "Mapping JPA pas à pas")
        @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères")
        String description,

        @Schema(description = "Catégorie (optionnel)", example = "1")
        Long categoryId,

        @Schema(description = "URL du fichier vidéo", example = "https://cdn.example.com/a.mp4")
        @NotBlank(message = "L'URL de la vidéo est obligatoire")
        @Size(max = 500, message = "L'URL de la vidéo ne doit pas dépasser 500 caractères")
        String videoUrl,

        @Schema(description = "URL miniature (optionnel)",
                example = "https://cdn.example.com/a.jpg")
        @Size(max = 500, message = "L'URL miniature ne doit pas dépasser 500 caractères")
        String thumbnailUrl,

        @Schema(description = "Durée en secondes", example = "540")
        @Min(value = 1, message = "La durée doit être positive")
        int durationSeconds
) {}