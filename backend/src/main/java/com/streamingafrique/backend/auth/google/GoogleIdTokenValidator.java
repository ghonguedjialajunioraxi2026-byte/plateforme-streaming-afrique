package com.streamingafrique.backend.auth.google;

import com.streamingafrique.backend.exception.ApiException;

/**
 * Vérification d'un id_token Google (OpenID Connect).
 *
 * L'implémentation de production contrôle, via la bibliothèque officielle
 * Google : signature RSA (certificats Google mis en cache), émetteur
 * (iss = accounts.google.com), audience (aud = notre GOOGLE_CLIENT_ID)
 * et expiration (exp). TOUT échec → {@link ApiException} 401 générique
 * (aucun détail exploitable, aucun secret, aucune pile).
 *
 * Interface dédiée (et non appel direct) pour rester testable sans réseau :
 * les tests injectent un stub qui rejoue les 5 cas (nouveau, existant,
 * conflit, refusé, expiré).
 */
public interface GoogleIdTokenValidator {

    /**
     * Vérifie l'id_token brut reçu du frontend et en extrait l'identité.
     *
     * @param idToken id_token OIDC remis par Google Identity Services
     * @return identité Google vérifiée (jamais null)
     * @throws ApiException 401 si le token est invalide, expiré, émis pour
     *         une autre application ou d'un émetteur inattendu ; 503 si la
     *         connexion Google n'est pas configurée côté serveur
     */
    GoogleProfile verify(String idToken);
}
