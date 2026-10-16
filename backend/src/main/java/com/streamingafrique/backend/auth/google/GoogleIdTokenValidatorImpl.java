package com.streamingafrique.backend.auth.google;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.streamingafrique.backend.exception.ApiException;

/**
 * Vérification PRODUCTION d'un id_token Google (bibliothèque officielle
 * {@code google-api-client} 2.9.1).
 *
 * Contrôles effectués par {@link GoogleIdTokenVerifier#verify(String)} :
 *   - signature RSA avec les certificats PUBLICS Google (mis en cache,
 *     rotation automatique) ;
 *   - émetteur {@code iss} = {@code accounts.google.com} ;
 *   - audience {@code aud} = notre {@code GOOGLE_CLIENT_ID} (un token émis
 *     pour une autre application est rejeté) ;
 *   - expiration {@code exp} (horloge serveur, tolérance interne).
 *
 * Pourquoi cette bibliothèque : vérification crypto OIDC correcte sans
 * réinventer JWKS/RSA (zéro code maison faillible), version épinglée au
 * pom, aucune dépendance Spring ajoutée, aucun secret requis.
 *
 * Sécurité : le token brut n'est JAMAIS journalisé (même en DEBUG) ;
 * les échecs remontent en 401 générique "Session Google invalide ou expirée".
 */
@Service
public class GoogleIdTokenValidatorImpl implements GoogleIdTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenValidatorImpl.class);

    private final GoogleProperties properties;

    public GoogleIdTokenValidatorImpl(GoogleProperties properties) {
        this.properties = properties;
    }

    @Override
    public GoogleProfile verify(String idToken) {
        String clientId = properties.getClientId() == null ? "" : properties.getClientId().trim();
        if (clientId.isEmpty()) {
            log.warn("Connexion Google tentée mais GOOGLE_CLIENT_ID non configuré");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OAUTH_NOT_CONFIGURED",
                    "Connexion Google momentanément indisponible");
        }
        if (idToken == null || idToken.isBlank()) {
            throw ApiException.unauthorized("Session Google invalide ou expirée");
        }
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singleton(clientId))
                    .build();
            GoogleIdToken token = verifier.verify(idToken.trim());
            if (token == null) {
                // Signature invalide, audience/émetteur inattendu ou token expiré.
                // Message volontairement générique : aucun détail exploitable.
                log.info("id_token Google rejeté (signature/audience/expiration)");
                throw ApiException.unauthorized("Session Google invalide ou expirée");
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String subject = payload.getSubject();
            Object emailClaim = payload.get("email");
            if (subject == null || subject.isBlank() || !(emailClaim instanceof String email)
                    || email.isBlank()) {
                log.info("id_token Google sans sub/email exploitable");
                throw ApiException.unauthorized("Session Google invalide ou expirée");
            }
            return new GoogleProfile(
                    subject,
                    email,
                    Boolean.TRUE.equals(payload.getEmailVerified()),
                    stringClaim(payload, "given_name"),
                    stringClaim(payload, "family_name"),
                    stringClaim(payload, "name"),
                    stringClaim(payload, "picture"));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // Erreur réseau Google, JSON illisible… : 401 générique côté client,
            // cause en DEBUG uniquement (jamais le token, jamais de secret).
            log.debug("Vérification Google impossible : {}", e.getMessage());
            throw ApiException.unauthorized("Session Google invalide ou expirée");
        }
    }

    private static String stringClaim(GoogleIdToken.Payload payload, String name) {
        Object value = payload.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        // Garde-fou : les claims sont stockés tels quels (nom/photo ≤ colonnes).
        String trimmed = text.trim();
        return switch (name) {
            case "picture" -> trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
            case "name", "given_name", "family_name" ->
                    trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
            default -> trimmed;
        };
    }
}
