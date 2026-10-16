package com.streamingafrique.backend.stream.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Branche {@link StreamingProperties} ({@code app.streaming.*}) sur le
 * contexte Spring (même pattern que Redis, JWT, rate-limit…).
 */
@Configuration
@EnableConfigurationProperties(StreamingProperties.class)
public class StreamingConfig {
}
