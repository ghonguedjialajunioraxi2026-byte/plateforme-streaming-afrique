package com.streamingafrique.backend.presence;

import com.streamingafrique.backend.presence.service.PresenceService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tâches planifiées de la présence (désactivables via {@code app.presence.enabled}
 * pour laisser les tests piloter la présence à la main).
 *
 *  - heartbeat : tant qu'un utilisateur a des sessions ouvertes sur cette
 *    instance, son marqueur Redis est « touché » (TTL prolongé + lastSeenAt
 *    actualisé). Une session active ne peut donc PAS expirer par erreur.
 *  - sweep : balayage périodique des marqueurs obsolètes (client disparu sans
 *    déconnexion propre) → passage OFFLINE diffusé, ensemble « online » épuré.
 */
@Component
public class PresenceScheduler {

    private final PresenceSessionRegistry registry;
    private final PresenceService presenceService;
    private final PresenceProperties properties;

    public PresenceScheduler(PresenceSessionRegistry registry,
                             PresenceService presenceService,
                             PresenceProperties properties) {
        this.registry = registry;
        this.presenceService = presenceService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.presence.heartbeat-interval-ms}")
    public void heartbeat() {
        if (!properties.isEnabled()) {
            return;
        }
        registry.activeUserIds().forEach(presenceService::touch);
    }

    @Scheduled(fixedDelayString = "${app.presence.sweep-interval-ms}")
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        presenceService.sweep();
    }
}