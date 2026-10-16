package com.streamingafrique.backend.clip.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Découpage d'un clip depuis une VOD ou un direct (étape 15, §34).
 *
 * Le frontend n'envoie QUE l'intention (titre + bornes + miniature
 * optionnelle) : durée, URL, chaîne source et auteur sont calculés/vérifiés
 * CÔTÉ SERVEUR (§35). Ni {@code endTime} ni {@code duration} ne sont
 * exposés ici : {@code startTime + duration} suffit, le serveur borne
 * {@code startTime + duration <= durée source} et {@code duration <= max}.
 */
public record CreateClipSegmentRequest(

        @Schema(description = "Titre du clip", example = "Moment incroyable")
        @NotBlank(message = "Le titre du clip est obligatoire")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
        String title,

        @Schema(description = "Début de l'extrait dans la source (secondes)", example = "5150")
        @Min(value = 0, message = "Le début doit être positif")
        int startTimeSeconds,

        @Schema(description = "Durée de l'extrait (secondes, bornée par le serveur)", example = "25")
        @Min(value = 1, message = "La durée doit être positive")
        int durationSeconds,

        @Schema(description = "Miniature (optionnel, sinon celle de la source)",
                example = "https://cdn.example.com/c.jpg")
        @Size(max = 500, message = "L'URL miniature ne doit pas dépasser 500 caractères")
        String thumbnailUrl
) {
}
