package com.streamingafrique.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Active la classe {@link StorageProperties} (@ConfigurationProperties) :
 * ses valeurs sont liées au démarrage depuis application.yml et les profils.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
}