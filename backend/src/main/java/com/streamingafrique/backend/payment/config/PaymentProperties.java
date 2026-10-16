package com.streamingafrique.backend.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres des paiements (étape 14).
 *
 * <pre>
 *   sandbox-enabled      : true en dev/test (fournisseur simulé), false en prod ;
 *   webhook-secret       : secret HMAC-SHA256 de signature des webhooks
 *                          (jamais dans Git : variable d'environnement) ;
 *   expiry-after         : un paiement resté INITIATED/PENDING au-delà
 *                          bascule en EXPIRED (jamais crédité) ;
 *   expiry-check-interval-ms : cadence du scheduler d'expiration ;
 *   expiry-enabled       : interrupteur du scheduler (tests à la main).
 * </pre>
 *
 * Surcharges par variables d'environnement : PAYMENTS_SANDBOX_ENABLED,
 * PAYMENT_WEBHOOK_SECRET, PAYMENTS_EXPIRY_AFTER, PAYMENTS_EXPIRY_CHECK_INTERVAL_MS,
 * PAYMENTS_EXPIRY_ENABLED.
 */
@ConfigurationProperties(prefix = "app.payments")
public class PaymentProperties {

    /** Sandbox actif : les prestataires réels sont remplacés par le simulé. */
    private boolean sandboxEnabled = true;

    /** Secret HMAC des signatures webhook (vide = webhooks refusés hors sandbox). */
    private String webhookSecret = "";

    /** Âge maximal d'un paiement non confirmé avant expiration. */
    private Duration expiryAfter = Duration.ofMinutes(30);

    /** Cadence (ms) du balayage d'expiration. */
    private long expiryCheckIntervalMs = 300_000;

    /** Active/désactive le scheduler d'expiration. */
    private boolean expiryEnabled = true;

    public boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    public void setSandboxEnabled(boolean sandboxEnabled) {
        this.sandboxEnabled = sandboxEnabled;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public Duration getExpiryAfter() {
        return expiryAfter;
    }

    public void setExpiryAfter(Duration expiryAfter) {
        this.expiryAfter = expiryAfter;
    }

    public long getExpiryCheckIntervalMs() {
        return expiryCheckIntervalMs;
    }

    public void setExpiryCheckIntervalMs(long expiryCheckIntervalMs) {
        this.expiryCheckIntervalMs = expiryCheckIntervalMs;
    }

    public boolean isExpiryEnabled() {
        return expiryEnabled;
    }

    public void setExpiryEnabled(boolean expiryEnabled) {
        this.expiryEnabled = expiryEnabled;
    }
}
