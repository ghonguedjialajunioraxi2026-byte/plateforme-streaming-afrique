package com.streamingafrique.backend.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
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
import com.streamingafrique.backend.payment.model.CoinPackage;
import com.streamingafrique.backend.payment.repository.CoinPackageRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Étape 14 — sécurité HTTP des paiements (vrai serveur + filtres).
 *
 * Couvre : routes sensibles anonymes → 401, webhook PUBLIC mais signature
 * exigée (sans signature → 401, RIEN persisté), création authentifiée
 * (Basic) → 201 avec montants serveur, simulateur authentifié.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.payments.webhook-secret=test-secret-webhook-14")
class PaymentSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CoinPackageRepository packs;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String PASSWORD = "motdepasse-etape-14";

    private User createUser(User.Role role) {
        String email = "pay14-" + Instant.now().toEpochMilli() + "-"
                + new Random().nextInt(100_000) + "@example.com";
        User user = User.create(email, "Payeur 14", passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    private CoinPackage createPack() {
        return packs.save(CoinPackage.create(
                "Pack Sec " + Instant.now().toEpochMilli() + "-" + new Random().nextInt(100_000),
                500L, new BigDecimal("500.0000"), "XOF", 1, null));
    }

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body,
                                                 String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (email != null) {
            headers.setBasicAuth(email, password);
        }
        return new HttpEntity<>(body, headers);
    }

    // -------------------------------------------------- anonymes → 401

    @Test
    void creationAnonyme401() {
        ResponseEntity<String> response = restTemplate.exchange("/api/payments",
                HttpMethod.POST, json(Map.of("packageId", 1, "provider", "WAVE",
                        "method", "MOBILE_MONEY"), null, null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void catalogueAnonyme401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/coin-packages", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void portefeuilleAnonyme401() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/wallet/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void webhookSansSignature401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"internalRef\":\"pay-x\",\"status\":\"SUCCEEDED\","
                + "\"fiatAmount\":\"500.0000\",\"fiatCurrency\":\"XOF\"}";

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/payments/webhooks/WAVE", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        // Route PUBLIQUE au filtre, mais preuve HMAC exigée : sans signature → 401.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -------------------------------------------------- authentifié (Basic)

    @Test
    void creationAuthentifiee201AvecMontantsServeur() throws Exception {
        User user = createUser(User.Role.USER);
        CoinPackage pack = createPack();

        ResponseEntity<String> response = restTemplate.exchange("/api/payments",
                HttpMethod.POST, json(Map.of("packageId", pack.getId(), "provider", "WAVE",
                        "method", "MOBILE_MONEY", "idempotencyKey", "sec-"
                                + Instant.now().toEpochMilli()),
                        user.getEmail(), PASSWORD), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("coinsAmount").asLong()).isEqualTo(500L);
        assertThat(data.path("fiatCurrency").asText()).isEqualTo("XOF");
        assertThat(data.path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void catalogueEtPortefeuilleAuthentifies200() {
        User user = createUser(User.Role.USER);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(user.getEmail(), PASSWORD);
        HttpEntity<Void> auth = new HttpEntity<>(headers);

        assertThat(restTemplate.exchange("/api/coin-packages", HttpMethod.GET,
                auth, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange("/api/wallet/me", HttpMethod.GET,
                auth, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Non-régression (chemin PRODUCTION, sans transaction englobante) :
     * création → simulation sandbox → statut CONFIRMED persisté + wallet
     * crédité + rejeu idempotent. Un auto-appel de service sans @Transactional
     * sur le point d'entrée perdrait la transition (crédit committé, statut
     * PENDING) — ce test l'interdit.
     */
    @Test
    void parcoursCompletConfirmeEtCrediteDeFaconAtomique() throws Exception {
        User user = createUser(User.Role.USER);
        CoinPackage pack = createPack();
        String key = "e2e-" + Instant.now().toEpochMilli();

        ResponseEntity<String> created = restTemplate.exchange("/api/payments",
                HttpMethod.POST, json(Map.of("packageId", pack.getId(), "provider", "WAVE",
                        "method", "MOBILE_MONEY", "idempotencyKey", key),
                        user.getEmail(), PASSWORD), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdData = objectMapper.readTree(created.getBody()).path("data");
        long paymentId = createdData.path("id").asLong();
        String internalRef = createdData.path("internalRef").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(user.getEmail(), PASSWORD);
        ResponseEntity<String> simulated = restTemplate.exchange(
                "/api/payments/sandbox/simulate", HttpMethod.POST,
                new HttpEntity<>(Map.of("internalRef", internalRef, "outcome", "SUCCEEDED"),
                        headers),
                String.class);
        assertThat(simulated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(simulated.getBody()).path("data").asText())
                .isEqualTo("CONFIRMED");

        // Statut PERSISTÉ (relu dans une transaction séparée) + wallet crédité.
        HttpEntity<Void> auth = new HttpEntity<>(basic(user));
        JsonNode reloaded = objectMapper.readTree(restTemplate.exchange(
                "/api/payments/" + paymentId, HttpMethod.GET, auth, String.class)
                .getBody()).path("data");
        assertThat(reloaded.path("status").asText()).isEqualTo("CONFIRMED");
        JsonNode wallet = objectMapper.readTree(restTemplate.exchange(
                "/api/wallet/me", HttpMethod.GET, auth, String.class)
                .getBody()).path("data");
        assertThat(wallet.path("balanceCoins").asLong()).isEqualTo(500L);

        // Rejeu → déjà traité, solde inchangé.
        ResponseEntity<String> replayed = restTemplate.exchange(
                "/api/payments/sandbox/simulate", HttpMethod.POST,
                new HttpEntity<>(Map.of("internalRef", internalRef, "outcome", "SUCCEEDED"),
                        headers),
                String.class);
        assertThat(objectMapper.readTree(replayed.getBody()).path("data").asText())
                .isEqualTo("ALREADY_PROCESSED");
        JsonNode walletAfter = objectMapper.readTree(restTemplate.exchange(
                "/api/wallet/me", HttpMethod.GET, auth, String.class)
                .getBody()).path("data");
        assertThat(walletAfter.path("balanceCoins").asLong()).isEqualTo(500L);
    }

    private static HttpHeaders basic(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(user.getEmail(), PASSWORD);
        return headers;
    }
}
