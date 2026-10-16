package com.streamingafrique.backend.video.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enregistre {@link VideoProperties} (préfixe {@code app.video}) et active
 * l'exécution asynchrone du traitement média (étape 15, §14) : le worker
 * tourne sur un pool dédié, JAMAIS sur le thread de la requête HTTP.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(VideoProperties.class)
public class VideoProcessingConfig {
}
