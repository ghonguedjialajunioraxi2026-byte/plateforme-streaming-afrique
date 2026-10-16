package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.StreamEventProperties;

import org.springframework.stereotype.Component;

/**
 * Resolution par défaut du topic d'un type d'événement :
 * {@code {baseTopic}} + {@code /{slug}}, soit {@code /topic/events/chat-message}
 * pour un {@code CHAT_MESSAGE}.
 *
 * Chaque type possède ainsi SA destination — l'abonné n'écoute que ce qui
 * l'intéresse — tandis que le répartiteur reste agnostique de la cible.
 */
@Component
public class TopicEventDestinationResolver implements EventDestinationResolver {

    private final StreamEventProperties properties;

    public TopicEventDestinationResolver(StreamEventProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(EventType type) {
        return properties.getBaseTopic() + "/" + type.slug();
    }
}