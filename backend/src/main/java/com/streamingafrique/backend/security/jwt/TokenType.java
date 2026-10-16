package com.streamingafrique.backend.security.jwt;

/**
 * Nature d'un jeton JWT émis par la plateforme.
 *
 * {@code ACCESS} est le seul livré à cette étape (6.24) : c'est le jeton que
 * le client présente dans {@code Authorization: Bearer <token>} pour appeler
 * l'API. Sa durée de vie est courte ({@code app.security.jwt.access-token-ttl}).
 *
 * {@code REFRESH} est PRÉPARÉ par l'architecture (durée de vie longue,
 * {@code refresh-token-ttl}, flux de renouvellement à venir) : le claim
 * {@code typ} du jeton distingue déjà les deux natures — lorsqu'on ajoutera
 * l'endpoint de rafraîchissement, la validation pourra refuser un token
 * ACCESS utilisé comme REFRESH et inversement, sans changer le noyau.
 */
public enum TokenType {
    ACCESS,
    REFRESH;

    /** Valeur du claim {@code typ} : ex. {@code ACCESS → "ACCESS"}. */
    public String claimValue() {
        return name();
    }
}