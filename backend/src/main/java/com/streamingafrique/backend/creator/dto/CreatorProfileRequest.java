package com.streamingafrique.backend.creator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE d'une fiche créateur. Le SLUG est généré automatiquement
 * depuis le displayName par le service (logique métier, pas ici).
 */
public record CreatorProfileRequest(

        @Schema(description = "Nom de la chaîne (slug public généré depuis ce nom)",
                example = "Créa TV")
        @NotBlank(message = "Le nom de chaîne est obligatoire")
        @Size(min = 2, max = 100, message = "Le nom de chaîne doit contenir entre 2 et 100 caractères")
        String displayName,

        @Schema(description = "Bio de la chaîne (optionnel)",
                example = "Tutoriels et directs chaque semaine")
        @Size(max = 500, message = "La bio ne doit pas dépasser 500 caractères")
        String bio,

        @Schema(description = "URL d'avatar (optionnel)",
                example = "https://cdn.example.com/avatars/crea.png")
        @Size(max = 500, message = "L'URL d'avatar ne doit pas dépasser 500 caractères")
        String avatarUrl,

        @Schema(description = "URL de bannière (optionnel)",
                example = "https://cdn.example.com/banners/crea.png")
        @Size(max = 500, message = "L'URL de bannière ne doit pas dépasser 500 caractères")
        String bannerUrl,

        @Schema(description = "Catégorie principale (optionnel, doit exister)",
                example = "1")
        Long primaryCategoryId
) {
    /** Compatibilité : sans catégorie principale (null). */
    public CreatorProfileRequest(String displayName, String bio, String avatarUrl, String bannerUrl) {
        this(displayName, bio, avatarUrl, bannerUrl, null);
    }
}