package com.streamingafrique.backend.websocket;

import com.streamingafrique.backend.config.WebSocketProperties;
import com.streamingafrique.backend.presence.service.PresenceService;
import com.streamingafrique.backend.websocket.dto.PulseMessage;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Battement de cœur de la démo : diffuse un {@link PulseMessage} sur
 * {@code /topic/pulse} tant qu'au moins un utilisateur est en ligne.
 *
 * C'est un exemple type des futurs usages du temps réel : un "flux poussé"
 * émis par le serveur sans que le client ait rien demandé (similaire aux
 * événements de stream ou aux compteurs en direct).
 */
@Service
public class WebSocketPulseService {

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final WebSocketProperties properties;
    private final AtomicLong sequence = new AtomicLong();

    public WebSocketPulseService(SimpMessagingTemplate messagingTemplate,
                                 PresenceService presenceService,
                                 WebSocketProperties properties) {
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
        this.properties = properties;
    }

    /** Émet un battement à la période configurée (app.websocket.demo-pulse-interval-ms). */
    @Scheduled(fixedRateString = "${app.websocket.demo-pulse-interval-ms}")
    public void pulse() {
        long online = presenceService.countOnline();
        if (online <= 0) {
            // Personne à l'écoute : inutile d'injecter du trafic dans le broker.
            return;
        }
        messagingTemplate.convertAndSend("/topic/pulse",
                new PulseMessage(sequence.incrementAndGet(), (int) online, Instant.now()));
    }
}