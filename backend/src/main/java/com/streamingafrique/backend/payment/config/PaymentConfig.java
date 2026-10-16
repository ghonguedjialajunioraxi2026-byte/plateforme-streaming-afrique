package com.streamingafrique.backend.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistre {@link PaymentProperties} (préfixe {@code app.payments}) —
 * même convention que les autres modules (rate-limit, Redis, présence…).
 */
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig {
}
