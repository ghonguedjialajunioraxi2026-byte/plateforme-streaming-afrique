package com.streamingafrique.backend.clip.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres des clips (étape 15, §30).
 *
 * <pre>
 *   min-duration-seconds : durée minimale d'un extrait ;
 *   max-duration-seconds : durée maximale d'un extrait (60 s par défaut) ;
 *   view-window          : fenêtre anti-abus des vues (comme les VOD).
 * </pre>
 *
 * Surcharges : CLIP_MIN_DURATION, CLIP_MAX_DURATION, CLIP_VIEW_WINDOW.
 */
@ConfigurationProperties(prefix = "app.clips")
public class ClipProperties {

    private int minDurationSeconds = 3;

    private int maxDurationSeconds = 60;

    private Duration viewWindow = Duration.ofHours(1);

    public int getMinDurationSeconds() {
        return minDurationSeconds;
    }

    public void setMinDurationSeconds(int minDurationSeconds) {
        this.minDurationSeconds = minDurationSeconds;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public Duration getViewWindow() {
        return viewWindow;
    }

    public void setViewWindow(Duration viewWindow) {
        this.viewWindow = viewWindow;
    }
}
