package com.streamingafrique.backend.profile.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE — modification du profil connecté (étape 09).
 *
 * Tous champs OPTIONNELS (null = inchangé, PUT partiel toléré).
 * AUCUN champ role/status/password : la sécurité ne transite jamais ici
 * (voir AuthService pour le mot de passe, UserController ADMIN pour le rôle).
 * Les règles croisées (blank, URL http(s), normalisation) vivent dans
 * {@code ProfileService} — les annotations ne couvrent que format/taille.
 */
public record UpdateProfileRequest(

        @Schema(description = "Nom affiché (2-100, non vide si fourni)", example = "Ada Lovelace")
        @Size(min = 2, max = 100, message = "Le nom d'affichage doit contenir entre 2 et 100 caractères")
        String displayName,

        @Schema(description = "Bio (500 max)", example = "Dev et directs")
        @Size(max = 500, message = "La bio ne doit pas dépasser 500 caractères")
        String bio,

        @Schema(description = "Pays ISO alpha-2 (ex. SN, CI)", example = "SN")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Le pays doit être un code ISO à 2 lettres")
        String countryCode,

        @Schema(description = "Langue (ex. fr, en, wo)", example = "fr")
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$",
                message = "La langue doit être un code cohérent (ex. fr, en-US)")
        String language,

        @Schema(description = "Fuseau IANA (ex. Africa/Douala, UTC)", example = "Africa/Douala")
        @Size(max = 60, message = "Le fuseau horaire est invalide")
        String timezone,

        @Schema(description = "Devise préférée ISO-3 (ex. XAF)", example = "XAF")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "La devise doit être un code ISO-3")
        String preferredCurrency,

        @Schema(description = "Site personnel (URL http(s))", example = "https://example.com")
        @Size(max = 500, message = "L'URL du site ne doit pas dépasser 500 caractères")
        String websiteUrl,

        @Schema(description = "Pseudo X (100 max)", example = "@ada")
        @Size(max = 100, message = "Le pseudo X ne doit pas dépasser 100 caractères")
        String xHandle,

        @Schema(description = "Pseudo Instagram (100 max)", example = "ada.lovelace")
        @Size(max = 100, message = "Le pseudo Instagram ne doit pas dépasser 100 caractères")
        String instagramHandle,

        @Schema(description = "Chaîne YouTube (URL http(s))", example = "https://youtube.com/@ada")
        @Size(max = 500, message = "L'URL YouTube ne doit pas dépasser 500 caractères")
        String youtubeUrl,

        @Schema(description = "Date de naissance (passée, sensible : jamais exposée en public)")
        @Past(message = "La date de naissance doit être dans le passé")
        LocalDate birthDate,

        @Schema(description = "Alertes de directs (null = inchangé)")
        Boolean notifyLive,

        @Schema(description = "Alertes de contenus (null = inchangé)")
        Boolean notifyContent,

        @Schema(description = "Profil visible publiquement (null = inchangé)")
        Boolean isPublic
) {}
