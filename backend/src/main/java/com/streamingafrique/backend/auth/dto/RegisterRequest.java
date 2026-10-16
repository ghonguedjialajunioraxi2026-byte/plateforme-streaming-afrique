package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Inscription (étape 08) — POST /api/auth/register.
 *
 * Le rôle est FORCÉ côté serveur (USER) : aucun champ {@code role} n'existe
 * ici — un client ne peut jamais s'attribuer ADMIN par requête.
 * La correspondance password/confirmPassword est vérifiée dans le service
 * (message d'erreur métier, pas de validation croisée par annotation).
 */
public record RegisterRequest(

        @Schema(description = "Pseudonyme unique (3-30, lettres/chiffres/._-)", example = "user237")
        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        @Size(min = 3, max = 30, message = "Le nom d'utilisateur doit contenir entre 3 et 30 caractères")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                message = "Le nom d'utilisateur ne peut contenir que lettres, chiffres, points, tirets et underscores")
        String username,

        @Schema(description = "Adresse email unique (normalisée en minuscules)", example = "user@example.com")
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "L'email doit être valide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        String email,

        @Schema(description = "Mot de passe (min 8, lettre + chiffre requis)", example = "MotDePasseFort123!")
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
        String password,

        @Schema(description = "Confirmation (doit être identique)", example = "MotDePasseFort123!")
        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        String confirmPassword,

        @Schema(description = "Code d'invitation bêta privée (requis si app.beta.invite-only)",
                example = "FLICK-BETA-9F3A2C1B4D5E6F78")
        @Size(max = 64, message = "Le code d'invitation ne doit pas dépasser 64 caractères")
        String inviteCode
) {}
