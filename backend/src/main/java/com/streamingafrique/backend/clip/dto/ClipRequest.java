package com.streamingafrique.backend.clip.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** DTO d'ENTRÉE d'un clip — la source (direct ou vidéo) est passée au service. */
public record ClipRequest(

        @Schema(description = "Titre du clip", example = "Meilleur moment")
        @NotBlank(message = "Le titre du clip est obligatoire")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
        String title,

        @Schema(description = "URL du fichier clip", example = "https://cdn.example.com/c.mp4")
        @NotBlank(message = "L'URL du clip est obligatoire")
        @Size(max = 500, message = "L'URL du clip ne doit pas dépasser 500 caractères")
        String clipUrl,

        @Schema(description = "URL miniature (optionnel)",
                example = "https://cdn.example.com/c.jpg")
        @Size(max = 500, message = "L'URL miniature ne doit pas dépasser 500 caractères")
        String thumbnailUrl,

        @Schema(description = "Durée en secondes", example = "25")
        @Min(value = 1, message = "La durée doit être positive")
        int durationSeconds
) {}