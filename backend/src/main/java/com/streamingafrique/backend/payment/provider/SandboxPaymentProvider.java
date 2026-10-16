package com.streamingafrique.backend.payment.provider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.payment.config.PaymentProperties;
import com.streamingafrique.backend.payment.model.Payment;

/**
 * Prestataire SIMULÉ (étape 14, §9 sandbox).
 *
 * RÔLE : stand-in de développement/test en attendant les intégrations réelles
 * (Wave, Orange Money, MTN MoMo, Stripe… — voir §70 : leurs API, sandbox,
 * webhooks et signatures seront vérifiés dans leurs documentations officielles
 * avant câblage, via {@link PaymentProvider} + registre).
 *
 * Contrat webhook sandbox (documenté, réutilisable par web/mobile/test) :
 * <pre>
 *   POST /api/payments/webhooks/{provider}
 *   X-Webhook-Signature: &lt;HMAC-SHA256 hex du corps brut, secret PAYMENT_WEBHOOK_SECRET&gt;
 *   {
 *     "internalRef": "pay-...",
 *     "providerRef": "sbx-...",
 *     "status": "SUCCEEDED" | "FAILED" | "PENDING",
 *     "fiatAmount": "1000.0000",
 *     "fiatCurrency": "XOF",
 *     "failureReason": "..." (FAILED uniquement)
 *   }
 * </pre>
 *
 * Sécurité : sans secret configuré, TOUT webhook est rejeté (401) — jamais
 * de crédit sur simple appel d'URL. Aucune donnée sensible n'est journalisée
 * (ni secret, ni signature complète).
 */
@Component
public class SandboxPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(SandboxPaymentProvider.class);

    static final String KEY = "SANDBOX";
    static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;

    public SandboxPaymentProvider(PaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Set<Payment.Method> supportedMethods() {
        return EnumSet.allOf(Payment.Method.class);
    }

    @Override
    public ProviderInitiation initiate(Payment payment) {
        String providerRef = "sbx-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.info("Paiement sandbox initié (internalRef={}, providerRef={}, montant={} {})",
                payment.getInternalRef(), providerRef,
                payment.getFiatAmount(), payment.getFiatCurrency());
        return new ProviderInitiation(providerRef,
                ProviderInitiation.ActionType.INSTRUCTIONS,
                null,
                "Paiement simulé : confirmez depuis le simulateur sandbox, "
                        + "puis le solde sera crédité après vérification serveur.",
                Instant.now().plus(properties.getExpiryAfter()));
    }

    @Override
    public WebhookVerification verifyWebhook(String rawBody, String signature) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Webhook sandbox rejeté : aucun secret configuré (PAYMENT_WEBHOOK_SECRET)");
            throw ApiException.unauthorized("Signature webhook non configurée");
        }
        if (signature == null || signature.isBlank() || !constantTimeEquals(signature.trim(), sign(rawBody, secret))) {
            log.warn("Webhook sandbox rejeté : signature invalide");
            throw ApiException.unauthorized("Signature webhook invalide");
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(rawBody == null ? "" : rawBody);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Corps webhook illisible");
        }
        String internalRef = text(body, "internalRef");
        String status = text(body, "status");
        String fiatCurrency = text(body, "fiatCurrency");
        String amount = text(body, "fiatAmount");
        if (internalRef == null || status == null || fiatCurrency == null || amount == null) {
            throw ApiException.badRequest("Webhook incomplet (internalRef, status, fiatAmount, fiatCurrency requis)");
        }
        BigDecimal fiatAmount;
        try {
            fiatAmount = new BigDecimal(amount);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Montant webhook invalide");
        }
        WebhookVerification.Outcome outcome = switch (status.trim().toUpperCase()) {
            case "SUCCEEDED", "SUCCESS", "CONFIRMED", "COMPLETED" -> WebhookVerification.Outcome.SUCCEEDED;
            case "FAILED", "FAILURE", "ERROR" -> WebhookVerification.Outcome.FAILED;
            case "PENDING", "PROCESSING", "INITIATED" -> WebhookVerification.Outcome.PENDING;
            default -> WebhookVerification.Outcome.UNKNOWN;
        };
        return new WebhookVerification(true, text(body, "providerRef"), internalRef, outcome,
                fiatAmount, fiatCurrency, text(body, "failureReason"));
    }

    /**
     * Signe un corps de webhook (utilisé par le simulateur sandbox + les
     * tests — JAMAIS exposé tel quel : le secret reste côté serveur).
     */
    public String sign(String rawBody) {
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Secret webhook non configuré");
        }
        return sign(rawBody, secret);
    }

    static String sign(String rawBody, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] body = rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("Calcul HMAC impossible", e);
        }
    }

    /** Comparaison à temps constant (anti timing-attack sur la signature). */
    private static boolean constantTimeEquals(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
