package com.streamingafrique.backend.security;

import java.time.Instant;
import java.util.Random;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Socle sécurité (6.23) — vérifie de bout en bout (vrai serveur HTTP + filtre
 * Spring Security + base) :
 *   - routes publiques accessibles anonymement (health) ;
 *   - routes protégées : anonyme → 401 ;
 *   - authentification Basic email+mot de passe (BCrypt en base) ;
 *   - autorisation par rôle : mauvais rôle → 403, bon rôle → 200 ;
 *   - inscription avec mot de passe → passwordHash BCrypt non vide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityAuthorizationsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PASSWORD = "motdepasse-6.23";

    private User createUser(String bareEmail, User.Role role) {
        String email = "sec-" + Instant.now().toEpochMilli() + "-" + new Random().nextInt(1000)
                + "-" + bareEmail;
        User user = User.create(email, "Compte " + role, passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    // -------------------------------------------------- routes publiques

    @Test
    void health_est_public_sans_authentification() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // -------------------------------------------------- inscription avec mot de passe

    @Test
    void inscription_avec_mot_de_passe_hache_et_permet_de_se_connecter() throws Exception {
        String email = "sec-register-" + Instant.now().toEpochMilli() + "@test.fr";

        String body = """
                { "email": "%s", "displayName": "Nouvelle venue", "password": "%s" }
                """.formatted(email, PASSWORD);

        ResponseEntity<String> create = restTemplate.exchange("/api/users",
                HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()),
                String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotBlank();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();

        ResponseEntity<String> whoami = restTemplate
                .withBasicAuth(email, PASSWORD)
                .getForEntity("/api/secure/whoami", String.class);
        assertThat(whoami.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // -------------------------------------------------- routes protégées

    @Test
    void route_protegee_anonyme_renvoie_401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/secure/whoami", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    void whoami_avec_mot_de_passe_valide_renvoie_identite_et_authorities() throws Exception {
        User viewer = createUser("viewer@test.fr", User.Role.USER);

        ResponseEntity<String> response = restTemplate
                .withBasicAuth(viewer.getEmail(), PASSWORD)
                .getForEntity("/api/secure/whoami", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("data").path("principal").asText()).isEqualTo(viewer.getEmail());
        assertThat(body.path("data").path("authenticated").asBoolean()).isTrue();
        assertThat(body.toString()).contains("ROLE_USER");
    }

    @Test
    void mauvais_mot_de_passe_renvoie_401() {
        User viewer = createUser("viewer@test.fr", User.Role.USER);

        ResponseEntity<String> response = restTemplate
                .withBasicAuth(viewer.getEmail(), "mauvais-mdp")
                .getForEntity("/api/secure/whoami", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void compte_sans_mot_de_passe_ne_peut_pas_sauthentifier() {
        User user = userRepository.save(
                User.create("sec-nohash@test.fr", "Sans mot de passe", null));

        ResponseEntity<String> response = restTemplate
                .withBasicAuth(user.getEmail(), PASSWORD)
                .getForEntity("/api/secure/whoami", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------- autorisation par rôle

    @Test
    void route_admin_est_refusee_sans_le_role_admin() {
        User creator = createUser("creator@test.fr", User.Role.CREATOR);
        User moderator = createUser("moderator@test.fr", User.Role.MODERATOR);

        ResponseEntity<String> asCreator = restTemplate
                .withBasicAuth(creator.getEmail(), PASSWORD)
                .getForEntity("/api/admin/status", String.class);
        ResponseEntity<String> asModerator = restTemplate
                .withBasicAuth(moderator.getEmail(), PASSWORD)
                .getForEntity("/api/admin/status", String.class);

        assertThat(asCreator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(asModerator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(asModerator.getBody()).contains("FORBIDDEN");
    }

    @Test
    void route_admin_est_accessible_au_role_admin() throws Exception {
        User admin = createUser("admin@test.fr", User.Role.ADMIN);

        ResponseEntity<String> response = restTemplate
                .withBasicAuth(admin.getEmail(), PASSWORD)
                .getForEntity("/api/admin/status", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("message").asText()).contains(admin.getEmail());
    }

    @Test
    void route_moderator_accepte_moderateur_mais_pas_simple_createur() {
        User moderator = createUser("moderator@test.fr", User.Role.MODERATOR);
        User creator = createUser("creator@test.fr", User.Role.CREATOR);

        ResponseEntity<String> asModerator = restTemplate
                .withBasicAuth(moderator.getEmail(), PASSWORD)
                .getForEntity("/api/moderator/status", String.class);
        ResponseEntity<String> asCreator = restTemplate
                .withBasicAuth(creator.getEmail(), PASSWORD)
                .getForEntity("/api/moderator/status", String.class);

        assertThat(asModerator.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asCreator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void route_creator_accepte_createur_mais_refuse_simple_user() {
        User creator = createUser("creator@test.fr", User.Role.CREATOR);
        User user = createUser("user@test.fr", User.Role.USER);

        ResponseEntity<String> asCreator = restTemplate
                .withBasicAuth(creator.getEmail(), PASSWORD)
                .getForEntity("/api/creator/status", String.class);
        ResponseEntity<String> asUser = restTemplate
                .withBasicAuth(user.getEmail(), PASSWORD)
                .getForEntity("/api/creator/status", String.class);

        assertThat(asCreator.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asUser.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}