package com.streamingafrique.backend.auth.google;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistre {@link GoogleProperties} (préfixe {@code app.google}) —
 * même convention que les autres modules (JWT, Redis, présence…).
 */
@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class GoogleConfig {
}
