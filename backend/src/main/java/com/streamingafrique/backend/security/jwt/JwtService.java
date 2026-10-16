package com.streamingafrique.backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Création, validation et extraction des jetons JWT (étape 6.24).
 *
 * Jeton d'ACCÈS émis pour un compte, signé HS256 :
 *   sub  → email (identifiant de connexion, cohérent avec UserDetailsServiceImpl) ;
 *   uid  → identifiant du compte en base (pour les flux qui le réclament) ;
 *   roles→ authority "ROLE_<RÔLE>" (hasRole/hasAnyRole fonctionnent ensuite) ;
 *   typ  → nature du jeton {@link TokenType} (ACCESS | REFRESH) ;
 *   iss  → émetteur attendu (JwtProperties.issuer) ;
 *   iat / exp / jti → horodatage, expiration (TTL), identifiant unique du jeton.
 *
 * VALIDATION stricte : signature (clé), émetteur, type PRÉVU et expiration
 * sont tous vérifiés avant de considérer un jeton comme valide. Une expiration
 * ne nécessite aucune requête : le token d'accès est SANS ÉTAT (stateless).
 *
 * CLÉ (configuration sécurisée) : {@code JWT_SECRET} vient de l'environnement,
 * jamais de Git. Si elle fait moins de 32 octets, elle est DÉRIVÉE (SHA-256)
 * en clé HS256 valide — un avertissement le signale. Sans {@code JWT_SECRET} du
 * tout, une clé ÉPHÉMÈRE aléatoire est générée : les jetons ne survivent pas
 * au redémarrage (utile en tests, dangereux ailleurs → prévenir).
 *
 * <p>Étape 22 / SEC-004 : en profil {@code prod}, la tolérance disparaît —
 * secret absent ou &lt; 32 octets → démarrage REFUSÉ (échec rapide, fail-fast).
 * Une clé faible en production annulerait la garantie HS256 (recherche
 * exhaustive facilitée) ; hors prod, le comportement souple est conservé
 * (dev local, tests).</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final String issuer;

    public JwtService(JwtProperties properties) {
        this(properties, null);
    }

    /**
     * Construction gérée par Spring : l'{@link Environment} sert à détecter le
     * profil {@code prod} (SEC-004). Le constructeur à un argument reste
     * disponible (tests unitaires : mode souple, hors prod).
     */
    @Autowired
    public JwtService(JwtProperties properties, Environment environment) {
        this.signingKey = resolveSigningKey(properties.getSecret(), isProd(environment));
        this.accessTokenTtl = properties.getAccessTokenTtl();
        this.refreshTokenTtl = properties.getRefreshTokenTtl();
        this.issuer = properties.getIssuer();
    }

    private static boolean isProd(Environment environment) {
        if (environment == null) {
            return false;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /* ─── Génération ────────────────────────────────────────── */

    /** Émet un jeton d'ACCÈS pour un compte authentifié. */
    public String generateAccessToken(Long userId, String email, String roleAuthority) {
        return generate(userId, email, roleAuthority, TokenType.ACCESS, accessTokenTtl);
    }

    /**
     * Émet un jeton de RAFRAÎCHISSEMENT (flux à venir) : la mécanique est prête,
     * le endpoint de renouvellement l'utilisera — architecture compatible refresh.
     */
    public String generateRefreshToken(Long userId, String email, String roleAuthority) {
        return generate(userId, email, roleAuthority, TokenType.REFRESH, refreshTokenTtl);
    }

    /* ─── Extraction ────────────────────────────────────────── */

    /** Email (subject) porté par un jeton valide, ou {@code null} si invalide. */
    public String extractUsername(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.getSubject() : null;
    }

    /** Identifiant de compte ({@code uid}) porté par un jeton valide, ou {@code null}. */
    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get("uid", Long.class) : null;
    }

    /** Authority "ROLE_..." portée par un jeton valide, ou {@code null}. */
    public String extractRoleAuthority(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get(CLAIM_ROLES, String.class) : null;
    }

    /* ─── Validation ────────────────────────────────────────── */

    /** Durée de vie restante (secondes) d'un token d'accès — sert la réponse de login. */
    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /**
     * Le jeton est-il un token d'ACCÈS valide (signature, émetteur, type et
     * expiration) ? Cette lecture stateless est utilisée par le filtre d'authentification.
     */
    public boolean isValidAccessToken(String token) {
        return parseClaims(token, TokenType.ACCESS) != null;
    }

    /**
     * Le jeton est-il un token de RAFRAÎCHISSEMENT valide (étape 08) ?
     * La validité cryptographique ne suffit PAS : l'appelant doit en plus
     * vérifier la session en base (révocation, expiration, rotation).
     */
    public boolean isValidRefreshToken(String token) {
        return parseClaims(token, TokenType.REFRESH) != null;
    }

    /** Durée de vie d'un refresh token, en secondes — sert la réponse de login/refresh. */
    public long refreshTokenTtlSeconds() {
        return refreshTokenTtl.toSeconds();
    }

    /* ─── Internes ──────────────────────────────────────────── */

    private String generate(Long userId, String email, String roleAuthority,
                            TokenType type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, roleAuthority)
                .claim(CLAIM_TYPE, type.claimValue())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Parse et valide un jeton : signature, émetteur, expiration (type libre). */
    private Claims parseClaims(String token) {
        return parseClaims(token, null);
    }

    /**
     * Parse et valide un jeton : signature, émetteur, expiration, et — si un
     * type est exigé — le claim {@code typ} correspondant (ACCESS ≠ REFRESH).
     * Renvoie {@code null} pour TOUT jeton invalide/illisible (jamais d'exception).
     */
    private Claims parseClaims(String token, TokenType expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            var parser = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build();
            Claims claims = parser.parseSignedClaims(token).getPayload();
            if (expectedType != null) {
                String type = claims.get(CLAIM_TYPE, String.class);
                if (!expectedType.claimValue().equals(type)) {
                    return null;
                }
            }
            return claims;
        } catch (Exception e) {
            // Signature invalide, jeton expiré, JSON corrompu… : traitement sans état,
            // le filtre traite la requête comme anonyme (401 si la route est protégée).
            log.debug("Jeton rejeté : {}", e.getMessage());
            return null;
        }
    }

    /* ─── Clé de signature ──────────────────────────────────── */

    private SecretKey resolveSigningKey(String configured, boolean strict) {
        if (configured == null || configured.isBlank()) {
            if (strict) {
                throw new IllegalStateException(
                        "JWT_SECRET absent en production : définissez JWT_SECRET "
                                + "(≥ 32 octets) dans l'environnement — démarrage refusé.");
            }
            log.warn("JWT_SECRET absent : clé HMAC ÉPHÉMÈRE aléatoire générée — "
                    + "les jetons ne survivront PAS au redémarrage. "
                    + "Définissez JWT_SECRET dans l'environnement (jamais dans Git).");
            byte[] ephemeral = new byte[64];
            new SecureRandom().nextBytes(ephemeral);
            return Keys.hmacShaKeyFor(ephemeral);
        }
        byte[] raw = configured.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            if (strict) {
                throw new IllegalStateException(
                        "JWT_SECRET trop court (< 32 octets) en production : "
                                + "fournissez une clé d'au moins 32 octets "
                                + "(ex. openssl rand -base64 48) — démarrage refusé.");
            }
            log.warn("JWT_SECRET trop court (< 32 octets) : clé dérivée par SHA-256 "
                    + "pour satisfaire HS256. Préférez une clé d'au moins 32 octets.");
            return new SecretKeySpec(sha256(raw), "HmacSHA256");
        }
        return Keys.hmacShaKeyFor(raw);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}