package com.streamingafrique.backend.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authentification par jeton JWT (étape 6.24) — vérifie de bout en bout
 * (vrai serveur HTTP + filtre + base + jjwt) :
 *   - login valide → access token émis, décodable et porteur des claims ;
 *   - login invalide (email inconnu, mauvais mot de passe) → 401 ;
 *   - route protégée : sans token → 401, avec token valide → 200 ;
 *   - token falsifié / expiré → 401 ;
 *   - architecture refresh : un jeton REFRESH n'est PAS accepté comme
 *     token d'accès (claim "typ" distingué).
 *
 * Le test forge lui-même des jetons (expiré / refresh) avec le MÊME secret
 * {@code app.security.jwt.secret} fixé ici — il sait donc les fabriquer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.security.jwt.secret=cle-secrete-de-tests-64-octets-0123456789abcdef0123456789",
        "app.security.jwt.access-token-ttl=15m",
        "app.security.jwt.issuer=streaming-afrique-backend"
})
class JwtAuthenticationTest {

    private static final String PASSWORD = "motdepasse-6.24";
    private static final String TEST_SECRET = "cle-secrete-de-tests-64-octets-0123456789abcdef0123456789";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    // -------------------------------------------------- login

    @Test
    void login_valide_emet_un_access_token_decodable() throws Exception {
        User user = createUser("viewer@test.fr", User.Role.USER);

        JsonNode body = login(user, PASSWORD);

        assertThat(body.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(body.path("data").path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.path("data").path("expiresIn").asLong()).isGreaterThan(0);
        assertThat(body.path("data").path("role").asText()).isEqualTo("USER");

        String token = body.path("data").path("accessToken").asText();
        assertThat(jwtService.isValidAccessToken(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo(user.getEmail());
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
        assertThat(jwtService.extractRoleAuthority(token)).isEqualTo("ROLE_USER");
    }

    @Test
    void login_mauvais_mot_de_passe_renvoie_401() {
        createUser("viewer@test.fr", User.Role.USER);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login",
                jsonOf("{ \"email\": \"viewer@test.fr\", \"password\": \"mauvais-mdp\" }"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("INVALID_CREDENTIALS");
    }

    @Test
    void login_email_inconnu_renvoie_401() {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login",
                jsonOf("{ \"email\": \"inconnu@test.fr\", \"password\": \"" + PASSWORD + "\" }"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------- route protégée

    @Test
    void route_protegee_sans_token_renvoie_401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/secure/whoami", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    void route_protegee_avec_token_valide_renvoie_200() throws Exception {
        User user = createUser("viewer@test.fr", User.Role.USER);
        String token = login(user, PASSWORD).path("data").path("accessToken").asText();

        ResponseEntity<String> response = bearerGet("/api/secure/whoami", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("data").path("principal").asText()).isEqualTo(user.getEmail());
        assertThat(body.path("data").path("authenticated").asBoolean()).isTrue();
        assertThat(body.path("data").path("authorities").toString()).contains("ROLE_USER");
    }

    @Test
    void route_protegee_avec_token_falsifie_renvoie_401() throws Exception {
        User user = createUser("viewer@test.fr", User.Role.USER);
        String token = login(user, PASSWORD).path("data").path("accessToken").asText();

        // On corrompt la signature (PREMIER caractère du 3e segment) : ces 6 bits
        // sont tous utiles, donc le jeton devient invalide à coup sûr. (Le dernier
        // caractère porte 2 bits de padding ignorés au décodage base64url : le
        // falsifier ne garantissait l'invalidité qu'une fois sur quatre — flaky.)
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        char tampered = (first == 'A') ? 'B' : 'A';
        String invalid = parts[0] + "." + parts[1] + "." + tampered + parts[2].substring(1);

        ResponseEntity<String> response = bearerGet("/api/secure/whoami", invalid);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void route_protegee_avec_token_expire_renvoie_401() throws Exception {
        User user = createUser("viewer@test.fr", User.Role.USER);

        String expiredToken = expiredAccessToken(user);

        ResponseEntity<String> response = bearerGet("/api/secure/whoami", expiredToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------- architecture refresh

    @Test
    void un_token_refresh_n_est_pas_accepte_comme_token_d_acces() {
        User user = createUser("viewer@test.fr", User.Role.USER);

        String refresh = jwtService.generateRefreshToken(user.getId(), user.getEmail(), "ROLE_USER");

        assertThat(jwtService.isValidAccessToken(refresh))
                .as("un jeton REFRESH ne doit pas ouvrir les routes du jeton d'accès").isFalse();
    }

    @Test
    void un_token_d_acces_valide_est_signature_type_access_et_expire_a_terme() {
        User user = createUser("viewer@test.fr", User.Role.USER);

        var claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(jwtService.generateAccessToken(user.getId(), user.getEmail(), "ROLE_USER"))
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(user.getEmail());
        assertThat(claims.get("uid", Long.class)).isEqualTo(user.getId());
        assertThat(claims.get("typ", String.class)).isEqualTo("ACCESS");
        assertThat(claims.getExpiration()).isAfter(new Date());
        assertThat(claims.getIssuer()).isEqualTo("streaming-afrique-backend");
    }

    /* ─── Helpers ────────────────────────────────────────────── */

    private User createUser(String email, User.Role role) {
        String unique = email.replace("@", "-" + Instant.now().toEpochMilli() + "@");
        User user = User.create(unique, "Compte " + role, passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    private JsonNode login(User user, String password) throws Exception {
        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login",
                jsonOf("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(user.getEmail(), password)),
                String.class);
        assertThat(response.getStatusCode())
                .as("login de %s", user.getEmail()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> bearerGet(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /** Construit un jeton d'accès DÉJÀ EXPIRÉ, signé avec le secret des tests. */
    private String expiredAccessToken(User user) {
        Date past = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("roles", "ROLE_" + user.getRole().name())
                .claim("typ", "ACCESS")
                .issuer("streaming-afrique-backend")
                .issuedAt(past)
                .expiration(past)
                .id(UUID.randomUUID().toString())
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private static HttpEntity<String> jsonOf(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}