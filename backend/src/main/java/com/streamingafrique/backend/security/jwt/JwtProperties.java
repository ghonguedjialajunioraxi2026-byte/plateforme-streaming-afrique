package com.streamingafrique.backend.security.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres JWT {@code app.security.jwt.*}.
 *
 *  - {@code secret}            : clé de signature HMAC-SHA256 (HS256), fournie
 *                                par l'ENVIRONNEMENT ({@code JWT_SECRET}),
 *                                jamais versionnée dans Git.
 *  - {@code accessTokenTtl}    : durée de vie d'un TOKEN D'ACCÈS (court).
 *  - {@code refreshTokenTtl}   : durée de vie d'un futur token de rafraîchissement
 *                                (architecture PRÉPARÉE, non livrée à cette étape).
 *  - {@code issuer}            : émetteur attendu dans le claim {@code iss}.
 *
 * Surcharges par variables d'environnement : JWT_SECRET, JWT_ACCESS_TOKEN_TTL,
 * JWT_REFRESH_TOKEN_TTL, JWT_ISSUER.
 *
 * POLITIQUE DE SECRET (configuration sécurisée) :
 *  - AUCUN secret n'existe dans Git ; il vient obligatoirement de l'environnement.
 *  - Si {@code JWT_SECRET} est absent, une clé ÉPHÉMÈRE aléatoire est générée au
 *    démarrage : l'application démarre mais les jetons ne survivent PAS au
 *    redémarrage (un avertissement est journalisé). Jamais de clé prédictible
 *    ni de secret « par défaut » embarqué.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** Clé de signature HS256 (env {@code JWT_SECRET}). Peut être vide → clé éphémère. */
    private String secret = "";

    /** Durée de vie d'un token d'accès (défaut : 15 minutes). */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    /** Durée de vie d'un futur token de rafraîchissement (défaut : 7 jours). */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** Émetteur attendu dans le claim {@code iss}. */
    private String issuer = "streaming-afrique-backend";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}