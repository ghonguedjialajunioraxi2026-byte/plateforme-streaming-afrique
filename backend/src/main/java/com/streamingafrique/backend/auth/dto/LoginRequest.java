package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requête de connexion (étape 6.24) — POST /api/auth/login.
 *
 * @param email    identifiant de connexion (normalisé en minuscules au login)
 * @param password secret du compte (vérifié contre le hash BCrypt en base)
 */
public record LoginRequest(

        @Schema(description = "Identifiant de connexion (insensible à la casse)",
                example = "ada@example.com")
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        String email,

        @Schema(description = "Mot de passe du compte (jamais stocké ni renvoyé en clair)",
                example = "motdepasse-xyz")
        @NotBlank(message = "Le mot de passe est obligatoire")
        String password
) {}