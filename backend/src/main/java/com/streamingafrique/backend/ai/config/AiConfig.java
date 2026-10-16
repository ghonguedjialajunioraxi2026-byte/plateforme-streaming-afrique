package com.streamingafrique.backend.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Active {@link AiProperties} (préfixe {@code ai}). */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
