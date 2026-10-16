package com.streamingafrique.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Preuve Google pour la connexion / liaison (flux ID token OIDC).
 *
 * Le frontend transmet UNIQUEMENT l'id_token brut remis par Google
 * Identity Services — jamais un email seul (qui ne prouverait rien).
 * Le backend vérifie ce token (signature, émetteur, audience, expiration)
 * avant toute création/liaison de compte ou émission de JWT FLICK.
 */
public record GoogleIdTokenRequest(
        /** id_token OIDC brut remis par Google (jamais construit à la main). */
        @NotBlank(message = "Le jeton Google est obligatoire") String idToken
) {
}
