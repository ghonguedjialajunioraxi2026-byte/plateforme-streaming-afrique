package com.streamingafrique.backend.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres du système d'événements temps réel {@code app.stream-events}.
 *
 *  - {@code enabled}          : active/désactive la DIFFUSION WebSocket des
 *                               événements (les traitements internes restent
 *                               actifs : on peut journaliser sans diffuser).
 *  - {@code baseTopic}        : racine des topics d'événements (défaut
 *                               {@code /topic/events}); chaque type est diffusé
 *                               sur {@code {baseTopic}/{slug}}.
 *  - {@code demoEnabled}      : active l'émission périodique d'un événement
 *                               FICTIF de type CHAT_MESSAGE (démo, pas un chat).
 *  - {@code demoIntervalMs}   : période d'émission de l'événement fictif (ms).
 *  - {@code demoStreamId} / {@code demoAuthorId} : identifiants de la fiction
 *                               (direct + auteur du message de démonstration).
 *
 * Surcharges par variables d'environnement : STREAM_EVENTS_ENABLED,
 * STREAM_EVENTS_BASE_TOPIC, STREAM_EVENTS_DEMO_ENABLED,
 * STREAM_EVENTS_DEMO_INTERVAL_MS, STREAM_EVENTS_DEMO_STREAM_ID,
 * STREAM_EVENTS_DEMO_AUTHOR_ID.
 */
@ConfigurationProperties(prefix = "app.stream-events")
public class StreamEventProperties {

    /** Active la diffusion WebSocket des événements. */
    private boolean enabled = true;

    /** Racine des topics d'événements. */
    private String baseTopic = "/topic/events";

    /** Émet périodiquement un événement fictif (démo). */
    private boolean demoEnabled = false;

    /** Période de l'événement fictif (ms). */
    private long demoIntervalMs = 15_000;

    /** Identifiant du direct fictif (démo). */
    private long demoStreamId = 1;

    /** Identifiant de l'auteur fictif (démo). */
    private long demoAuthorId = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseTopic() {
        return baseTopic;
    }

    public void setBaseTopic(String baseTopic) {
        this.baseTopic = baseTopic;
    }

    public boolean isDemoEnabled() {
        return demoEnabled;
    }

    public void setDemoEnabled(boolean demoEnabled) {
        this.demoEnabled = demoEnabled;
    }

    public long getDemoIntervalMs() {
        return demoIntervalMs;
    }

    public void setDemoIntervalMs(long demoIntervalMs) {
        this.demoIntervalMs = demoIntervalMs;
    }

    public long getDemoStreamId() {
        return demoStreamId;
    }

    public void setDemoStreamId(long demoStreamId) {
        this.demoStreamId = demoStreamId;
    }

    public long getDemoAuthorId() {
        return demoAuthorId;
    }

    public void setDemoAuthorId(long demoAuthorId) {
        this.demoAuthorId = demoAuthorId;
    }
}