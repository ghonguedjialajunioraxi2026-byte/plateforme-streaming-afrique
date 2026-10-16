package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.StreamEventProperties;
import com.streamingafrique.backend.event.dto.ChatMessagePayload;
import com.streamingafrique.backend.presence.service.PresenceService;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Génère un événement FICTIF ({@code CHAT_MESSAGE}) à intervalle régulier pour
 * observer l'architecture en conditions réelles — pas un vrai système de chat.
 *
 * Gardes :
 *   - {@code app.stream-events.demo-enabled} : seule clé d'activation ;
 *   - au moins un utilisateur en ligne : pas de diffusion dans le vide
 *     (même philosophie que le pulse de WebSocketPulseService).
 *
 * À supprimer/remplacer quand les vrais flux métier publieront leurs événements.
 */
@Service
public class StreamEventDemoScheduler {

    private static final Logger log = LoggerFactory.getLogger(StreamEventDemoScheduler.class);

    private final StreamEventPublisher publisher;
    private final StreamEventProperties properties;
    private final PresenceService presenceService;

    public StreamEventDemoScheduler(StreamEventPublisher publisher,
                                    StreamEventProperties properties,
                                    PresenceService presenceService) {
        this.publisher = publisher;
        this.properties = properties;
        this.presenceService = presenceService;
    }

    @Scheduled(fixedRateString = "${app.stream-events.demo-interval-ms}")
    public void emitDemoEvent() {
        if (!properties.isDemoEnabled()) {
            return;
        }
        if (presenceService.countOnline() <= 0) {
            return;
        }
        ChatMessagePayload payload = new ChatMessagePayload(
                properties.getDemoStreamId(),
                properties.getDemoAuthorId(),
                "Démo",
                "Événement fictif de démonstration @" + Instant.now(),
                Instant.now());
        publisher.publish(EventType.CHAT_MESSAGE, payload);
        log.info("Événement fictif publié sur /topic/events/chat-message");
    }
}