package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.StreamEventProperties;
import com.streamingafrique.backend.event.dto.EventEnvelope;
import com.streamingafrique.backend.event.model.StreamEvent;

import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Le pont WebSocket des événements : un {@link StreamEventHandler} parmi les
 * autres, qui diffuse chaque événement sur le topic de son type
 * ({@link EventDestinationResolver}) sous forme d'{@link EventEnvelope}.
 *
 * Le fait qu'il soit un simple traitant enregistré auprès du répartiteur est
 * le cœur de l'extensibilité : d'autres transports (SSE, webhooks, files…)
 * s'ajouteraient de la même manière, sans toucher aux producteurs.
 *
 * La désactivation ({@code app.stream-events.enabled=false}) n'éteint QUE ce
 * traitant : les autres (journalisation, traitements métier) continuent.
 *
 * SimpMessagingTemplate injecté en {@link Lazy} : le broker STOMP qui le
 * fournit dépend des configurateurs WebSocket — un proxy différé évite toute
 * dépendance circulaire (même mesure que la présence).
 */
@Component
public class WebSocketStreamEventHandler implements StreamEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketStreamEventHandler.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final EventDestinationResolver destinationResolver;
    private final StreamEventProperties properties;

    public WebSocketStreamEventHandler(@Lazy SimpMessagingTemplate messagingTemplate,
                                       EventDestinationResolver destinationResolver,
                                       StreamEventProperties properties) {
        this.messagingTemplate = messagingTemplate;
        this.destinationResolver = destinationResolver;
        this.properties = properties;
    }

    @Override
    public Set<EventType> handles() {
        return EnumSet.allOf(EventType.class);
    }

    @Override
    public void handle(StreamEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        String destination = destinationResolver.resolve(event.type());
        messagingTemplate.convertAndSend(destination, event.toEnvelope());
        log.debug("Événement diffusé sur {} : {}", destination, event.type());
    }
}