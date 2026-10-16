package com.streamingafrique.backend.auth.google;

/**
 * Identité Google extraite d'un id_token OIDC VÉRIFIÉ (jamais de données
 * brutes du frontend : seul le validateur produit cette structure, après
 * contrôle de la signature, de l'émetteur, de l'audience et de l'expiration).
 *
 * Champs demandés : Google user ID (sub), email, prénom, nom, nom complet,
 * photo de profil, email_verified.
 */
public record GoogleProfile(
        /** Identifiant stable du compte Google (claim "sub"). */
        String googleId,
        /** Email du compte Google (normalisé en minuscules par le service). */
        String email,
        /** Google certifie-t-il cet email (claim "email_verified") ? */
        boolean emailVerified,
        /** Prénom (claim "given_name", peut être null). */
        String givenName,
        /** Nom (claim "family_name", peut être null). */
        String familyName,
        /** Nom complet (claim "name"). */
        String fullName,
        /** Photo de profil (claim "picture", URL Google, peut être null). */
        String pictureUrl
) {
}
