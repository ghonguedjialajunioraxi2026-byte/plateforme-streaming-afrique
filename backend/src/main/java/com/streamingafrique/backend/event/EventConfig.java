package com.streamingafrique.backend.event;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Active le binding des propriétés {@code app.stream-events.*}
 * (même approche que {@code PresenceConfig} pour {@code app.presence.*}).
 *
 * Le reste de l'architecture est auto-câblé par le scan de composants :
 * le {@code StreamEventDispatcher} recueille tous les beans {@code StreamEventHandler}
 * (diffusion WebSocket, journalisation, et tout abonné futur).
 */
@Configuration
@EnableConfigurationProperties(StreamEventProperties.class)
public class EventConfig {
}