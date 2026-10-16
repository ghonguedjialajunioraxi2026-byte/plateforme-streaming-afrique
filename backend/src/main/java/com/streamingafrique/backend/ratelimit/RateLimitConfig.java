package com.streamingafrique.backend.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistre {@link RateLimitProperties} (préfixe {@code app.rate-limit}) —
 * même convention que les autres modules (Redis, JWT, présence…).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
}
