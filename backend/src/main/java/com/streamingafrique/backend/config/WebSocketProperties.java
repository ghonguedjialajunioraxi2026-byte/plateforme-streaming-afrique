package com.streamingafrique.backend.config;

import java.util.Arrays;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres applicatifs WebSocket (temps réel).
 *
 *  - {@code path}              : chemin de la poignée de main WebSocket (défaut {@code /ws}).
 *  - {@code allowedOrigins}    : origines autorisées à établir une connexion
 *                                (même philosophie que {@code app.cors.allowed-origins}).
 *  - {@code demoPulseIntervalMs} : période du battement de démonstration diffusé
 *                                sur {@code /topic/pulse} (ms).
 *
 * Surcharges par variables d'environnement : WS_PATH, WS_ALLOWED_ORIGINS,
 * WS_DEMO_PULSE_INTERVAL_MS.
 */
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private String path = "/ws";

    private String allowedOrigins = "http://localhost:5173";

    private long demoPulseIntervalMs = 5000;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public long getDemoPulseIntervalMs() {
        return demoPulseIntervalMs;
    }

    public void setDemoPulseIntervalMs(long demoPulseIntervalMs) {
        this.demoPulseIntervalMs = demoPulseIntervalMs;
    }

    /**
     * Découpe la propriété {@code "https://a.com,https://b.com"} en une liste
     * d'origines propres (supprime espaces et entrées vides).
     */
    public String[] getAllowedOriginsArray() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}