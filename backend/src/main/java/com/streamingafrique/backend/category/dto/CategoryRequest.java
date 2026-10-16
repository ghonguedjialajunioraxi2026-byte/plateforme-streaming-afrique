package com.streamingafrique.backend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE (création + mise à jour d'une catégorie).
 * La validation démarre ici (Jakarta Validation) AVANT d'atteindre le service.
 */
public record CategoryRequest(

        @Schema(description = "Nom de la catégorie (slug généré depuis ce nom)",
                example = "Musique")
        @NotBlank(message = "Le nom de la catégorie est obligatoire")
        @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères")
        String name,

        @Schema(description = "Description (optionnel)", example = "Concerts et sessions live")
        @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
        String description
) {}