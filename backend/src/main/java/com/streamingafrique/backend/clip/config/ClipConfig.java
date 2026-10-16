package com.streamingafrique.backend.clip.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistre {@link ClipProperties} (préfixe {@code app.clips}) —
 * même convention que les autres modules.
 */
@Configuration
@EnableConfigurationProperties(ClipProperties.class)
public class ClipConfig {
}
