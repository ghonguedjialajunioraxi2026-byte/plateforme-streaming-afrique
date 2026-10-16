package com.streamingafrique.backend.presence;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres du système de présence utilisateur (backend bis).
 *
 *  - {@code ttl}                  : durée de vie d'un marqueur de présence Redis.
 *                                   Une session active « touche » le marqueur
 *                                   périodiquement (heartbeat) pour le prolonger ;
 *                                   à expiration, l'utilisateur repasse OFFLINE.
 *  - {@code heartbeatIntervalMs}  : fréquence à laquelle le serveur prolonge le
 *                                   TTL des marqueurs des sessions actives.
 *  - {@code sweepIntervalMs}      : fréquence du nettoyage des présences
 *                                   obsolètes (balayage + diffuse les offline).
 *
 * Surcharges par variables d'environnement : PRESENCE_TTL, PRESENCE_HEARTBEAT_INTERVAL_MS,
 * PRESENCE_SWEEP_INTERVAL_MS, PRESENCE_ENABLED.
 */
@ConfigurationProperties(prefix = "app.presence")
public class PresenceProperties {

    /** Durée de vie d'un marqueur de présence (expiration ⇒ OFFLINE automatique). */
    private Duration ttl = Duration.ofMinutes(2);

    /** Fréquence (ms) du heartbeat serveur qui prolonge les présences actives. */
    private long heartbeatIntervalMs = 30_000;

    /** Fréquence (ms) du nettoyage des présences obsolètes. */
    private long sweepIntervalMs = 60_000;

    /** Active/désactive les tâches planifiées (les tests les appellent à la main). */
    private boolean enabled = true;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public long getSweepIntervalMs() {
        return sweepIntervalMs;
    }

    public void setSweepIntervalMs(long sweepIntervalMs) {
        this.sweepIntervalMs = sweepIntervalMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}