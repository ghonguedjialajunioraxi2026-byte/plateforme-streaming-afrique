package com.streamingafrique.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE d'un compte.
 * Le champ {@code password} est OPTIONNEL : s'il est fourni, le mot de passe
 * est haché (BCrypt) et stocké dans {@code passwordHash}, ce qui permet
 * l'authentification Basic (socle sécurité 6.23). Sans mot de passe, le
 * compte reste public (aucune authentification possible) — comportement
 * conservé pour ne pas casser l'existant.
 */
public record UserRequest(

        @Schema(description = "Email du compte (normalisé en minuscules, unique)",
                example = "ada@example.com")
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        String email,

        @Schema(description = "Nom affiché publiquement", example = "Ada")
        @NotBlank(message = "Le nom d'affichage est obligatoire")
        @Size(max = 100, message = "Le nom d'affichage ne doit pas dépasser 100 caractères")
        String displayName,

        @Schema(description = "URL d'avatar (optionnel)",
                example = "https://cdn.example.com/avatars/ada.png")
        @Size(max = 500, message = "L'URL d'avatar ne doit pas dépasser 500 caractères")
        String avatarUrl,

        @Schema(description = "Mot de passe 8–100 caractères (optionnel, haché BCrypt en base)",
                example = "motdepasse-xyz")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
        String password
) {}