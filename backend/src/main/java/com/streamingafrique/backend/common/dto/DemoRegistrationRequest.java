package com.streamingafrique.backend.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO de démonstration pour tester la validation d'entrée.
 * Il illustre les annotations Jakarta Validation utilisées dans le projet :
 *   - @NotBlank : chaîne non vide (ni null)
 *   - @NotNull  : champ obligatoire (ici Boolean)
 *   - @Email    : format d'adresse email
 *   - @Size     : longueur min/max d'une chaîne
 *   - @Min/@Max : bornes numériques
 *
 * Chaque annotation porte un message lisible renvoyé dans la map
 * "errors" de la réponse ApiError.
 *
 * ⚠️ DTO de démo : les vrais DTO métier (user, stream, payment...)
 * suivront exactement ce même modèle.
 */
public record DemoRegistrationRequest(

        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        @Size(min = 3, max = 20, message = "Le nom d'utilisateur doit contenir entre 3 et 20 caractères")
        String username,

        @NotBlank(message = "L'adresse email est obligatoire")
        @Email(message = "Adresse email invalide")
        String email,

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 100, message = "Le mot de passe doit contenir entre 8 et 100 caractères")
        String password,

        @NotNull(message = "L'âge est obligatoire")
        @Min(value = 18, message = "L'âge minimum est 18 ans")
        @Max(value = 100, message = "L'âge maximum est 100 ans")
        Integer age,

        @NotNull(message = "Les conditions générales doivent être acceptées")
        Boolean acceptedTerms
) {}