package com.streamingafrique.backend.security.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Active le binding des propriétés {@code app.security.jwt.*}
 * (même approche que {@code PresenceConfig} / {@code EventConfig}).
 *
 * Le reste du socle JWT est auto-câblé par le scan de composants
 * ({@link JwtService}) et la {@code SecurityFilterChain} (SecurityConfig).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}