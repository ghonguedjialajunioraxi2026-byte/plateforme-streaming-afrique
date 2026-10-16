package com.streamingafrique.backend.video.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres vidéo VOD (étape 15).
 *
 * <pre>
 *   max-upload-mb  : taille maximale d'UN fichier vidéo téléversé ;
 *   allowed-types  : types MIME acceptés, vérifiés sur le CONTENU (ftyp MP4,
 *                    EBML WebM), jamais sur l'extension déclarée ;
 *   view-window    : fenêtre anti-abus des vues (un viewer = une vue max
 *                    par fenêtre, clé Redis avec TTL) ;
 *   ffmpeg-enabled : probe/découpe FFmpeg (désactivé sans binaire vérifié).
 * </pre>
 *
 * Surcharges : VIDEO_MAX_UPLOAD_MB, VIDEO_ALLOWED_TYPES, VIDEO_VIEW_WINDOW,
 * VIDEO_FFMPEG_ENABLED.
 */
@ConfigurationProperties(prefix = "app.video")
public class VideoProperties {

    private long maxUploadMb = 512;

    private List<String> allowedTypes = new ArrayList<>(List.of("video/mp4", "video/webm"));

    private Duration viewWindow = Duration.ofHours(1);

    private boolean ffmpegEnabled = false;

    private boolean recoveryEnabled = true;

    public long getMaxUploadMb() {
        return maxUploadMb;
    }

    public void setMaxUploadMb(long maxUploadMb) {
        this.maxUploadMb = maxUploadMb;
    }

    public List<String> getAllowedTypes() {
        return allowedTypes;
    }

    public void setAllowedTypes(List<String> allowedTypes) {
        this.allowedTypes = allowedTypes;
    }

    public Duration getViewWindow() {
        return viewWindow;
    }

    public void setViewWindow(Duration viewWindow) {
        this.viewWindow = viewWindow;
    }

    public boolean isFfmpegEnabled() {
        return ffmpegEnabled;
    }

    public void setFfmpegEnabled(boolean ffmpegEnabled) {
        this.ffmpegEnabled = ffmpegEnabled;
    }

    /** Rejeu au boot des traitements orphelins (tests à la main quand false). */
    public boolean isRecoveryEnabled() {
        return recoveryEnabled;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled) {
        this.recoveryEnabled = recoveryEnabled;
    }
}
