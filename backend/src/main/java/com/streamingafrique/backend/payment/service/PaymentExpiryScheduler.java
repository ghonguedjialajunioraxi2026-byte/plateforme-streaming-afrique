package com.streamingafrique.backend.payment.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.streamingafrique.backend.payment.config.PaymentProperties;

/**
 * Expiration des paiements abandonnés (étape 14, §33-34).
 *
 * Balaye les INITIATED/PENDING plus vieux que {@code app.payments.expiry-after}
 * et les fait basculer en EXPIRED (jamais crédités, jamais rejouables).
 * Désactivable ({@code app.payments.expiry-enabled=false}) pour laisser les
 * tests piloter l'expiration à la main — même convention que la présence.
 */
@Component
public class PaymentExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryScheduler.class);

    private final PaymentService payments;
    private final PaymentProperties properties;

    public PaymentExpiryScheduler(PaymentService payments, PaymentProperties properties) {
        this.payments = payments;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.payments.expiry-check-interval-ms}")
    public void expireAbandoned() {
        if (!properties.isExpiryEnabled()) {
            return;
        }
        try {
            int expired = payments.expireStale(Instant.now().minus(properties.getExpiryAfter()));
            if (expired > 0) {
                log.info("Scheduler paiements : {} expiré(s)", expired);
            }
        } catch (Exception e) {
            log.error("Scheduler paiements : échec du balayage d'expiration", e);
        }
    }
}
