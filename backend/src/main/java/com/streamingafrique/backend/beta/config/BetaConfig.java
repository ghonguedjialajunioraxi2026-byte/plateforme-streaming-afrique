package com.streamingafrique.backend.beta.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistre {@link BetaProperties} (préfixe {@code app.beta}) —
 * même convention que les autres modules (rate limiting, JWT, …).
 */
@Configuration
@EnableConfigurationProperties(BetaProperties.class)
public class BetaConfig {
}
