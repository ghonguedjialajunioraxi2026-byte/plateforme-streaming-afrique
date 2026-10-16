package com.streamingafrique.backend.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stratégie de tests (étape 6.29) — test UNITAIRE pur du service JWT.
 *
 * Complète le test d'intégration {@code JwtAuthenticationTest} (vrai serveur
 * HTTP + filtre) : ici on verrouille la mécanique des jetons SANS serveur
 * (millisecondes, aucun flaky réseau) — génération, claims, TTL, rejet des
 * jetons invalides, séparation ACCESS/REFRESH et politique de clé.
 */
class JwtServiceTest {

    private static final String SECRET = "cle-secrete-de-tests-64-octets-0123456789abcdef0123456789";

    private static JwtService service() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        properties.setIssuer("streaming-afrique-backend");
        return new JwtService(properties);
    }

    // -------------------------------------------------- génération / lecture

    @Test
    @DisplayName("Un jeton généré est valide et porte sub, uid, roles, typ=ACCESS, iss et expiration")
    void jeton_genere_valide_et_complet() {
        JwtService service = service();

        String token = service.generateAccessToken(42L, "viewer@test.fr", "ROLE_USER");

        assertThat(service.isValidAccessToken(token)).isTrue();
        assertThat(service.extractUsername(token)).isEqualTo("viewer@test.fr");
        assertThat(service.extractUserId(token)).isEqualTo(42L);
        assertThat(service.extractRoleAuthority(token)).isEqualTo("ROLE_USER");
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(15 * 60L);
    }

    // -------------------------------------------------- rejets

    @Test
    @DisplayName("Jetons null, vide, falsifié ou expiré → invalides, extracteurs à null (jamais d'exception)")
    void jetons_invalides() {
        JwtService service = service();
        String valid = service.generateAccessToken(1L, "u@test.fr", "ROLE_USER");

        assertThat(service.isValidAccessToken(null)).isFalse();
        assertThat(service.isValidAccessToken("   ")).isFalse();
        assertThat(service.isValidAccessToken(valid + "a")).isFalse();

        String expired = Jwts.builder()
                .subject("u@test.fr")
                .claim("uid", 1L)
                .claim("roles", "ROLE_USER")
                .claim("typ", "ACCESS")
                .issuer("streaming-afrique-backend")
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertThat(service.isValidAccessToken(expired)).isFalse();

        assertThat(service.extractUsername("jeton-pourri")).isNull();
        assertThat(service.extractUserId(null)).isNull();
        assertThat(service.extractRoleAuthority("")).isNull();
    }

    @Test
    @DisplayName("Mauvais émetteur (iss) → rejeté (anti jetons d'un autre service)")
    void mauvais_emetteur_rejete() {
        String foreign = Jwts.builder()
                .subject("u@test.fr")
                .claim("uid", 1L)
                .claim("roles", "ROLE_USER")
                .claim("typ", "ACCESS")
                .issuer("autre-service")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .id(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThat(service().isValidAccessToken(foreign)).isFalse();
    }

    @Test
    @DisplayName("Un REFRESH n'ouvre pas les routes d'accès (claim typ distingué)")
    void refresh_refuse_comme_access() {
        JwtService service = service();

        String refresh = service.generateRefreshToken(1L, "u@test.fr", "ROLE_USER");

        assertThat(service.isValidAccessToken(refresh)).isFalse();
    }

    // -------------------------------------------------- politique de clé

    @Test
    @DisplayName("Secret absent → clé éphémère : le service démarre mais deux instances sont incompatibles")
    void secret_absent_cle_ephemere() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("");
        JwtService first = new JwtService(properties);
        JwtService second = new JwtService(properties);

        String token = first.generateAccessToken(1L, "u@test.fr", "ROLE_USER");

        assertThat(first.isValidAccessToken(token)).isTrue();
        assertThat(second.isValidAccessToken(token))
                .as("deux clés éphémères différentes ne doivent pas se valider mutuellement")
                .isFalse();
    }

    @Test
    @DisplayName("Secret trop court (< 32 octets) → clé dérivée fonctionnelle (avertissement logué)")
    void secret_court_derive() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("trop-court");
        JwtService service = new JwtService(properties);

        String token = service.generateAccessToken(1L, "u@test.fr", "ROLE_USER");

        assertThat(service.isValidAccessToken(token)).isTrue();
    }

    // -------------------------------------------------- SEC-004 : fail-fast en prod

    private static org.springframework.mock.env.MockEnvironment prodEnv() {
        org.springframework.mock.env.MockEnvironment environment =
                new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private static JwtProperties propertiesWith(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        properties.setIssuer("streaming-afrique-backend");
        return properties;
    }

    @Test
    @DisplayName("SEC-004 : profil prod + secret absent → démarrage refusé")
    void prod_sans_secret_refuse() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new JwtService(propertiesWith(""), prodEnv())))
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("SEC-004 : profil prod + secret < 32 octets → démarrage refusé")
    void prod_secret_court_refuse() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new JwtService(propertiesWith("trop-court"), prodEnv())))
                .hasMessageContaining("32 octets");
    }

    @Test
    @DisplayName("SEC-004 : profil prod + secret ≥ 32 octets → accepté")
    void prod_secret_fort_accepte() {
        JwtService service = new JwtService(propertiesWith(SECRET), prodEnv());

        assertThat(service.isValidAccessToken(
                service.generateAccessToken(1L, "u@test.fr", "ROLE_USER"))).isTrue();
    }

    @Test
    @DisplayName("SEC-004 : hors prod, secret absent → éphémère toléré (dev/tests)")
    void hors_prod_sans_secret_tolere() {
        JwtService service = new JwtService(propertiesWith(""),
                new org.springframework.mock.env.MockEnvironment());

        assertThat(service.isValidAccessToken(
                service.generateAccessToken(1L, "u@test.fr", "ROLE_USER"))).isTrue();
    }
}
