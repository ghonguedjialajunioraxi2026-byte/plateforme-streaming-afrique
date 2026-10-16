package com.streamingafrique.backend.presence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Active le binding des propriétés {@code app.presence.*}
 * (même approche que {@code RedisCacheProperties} sur {@code app.redis.*}).
 */
@Configuration
@EnableConfigurationProperties(PresenceProperties.class)
public class PresenceConfig {
}