package com.streamingafrique.backend.stream.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres du livestreaming (étape 10).
 *
 * Le backend Spring n'est PAS un serveur vidéo : il orchestre un serveur
 * média externe (SRS en dev : RTMP ingest + HLS + hooks HTTP). Tous les
 * points de contact avec ce serveur sont configurables ici, jamais en dur.
 *
 * Surcharges par variables d'environnement : RTMP_INGEST_URL,
 * HLS_BASE_URL, INGEST_CALLBACK_SECRET, SRS_API_URL, etc.
 */
@ConfigurationProperties(prefix = "app.streaming")
public class StreamingProperties {

    /**
     * URL RTMP affichée au créateur pour OBS (hôte vu depuis son poste).
     * Le chemin applicatif est toujours {@code /live} ; la clé suit en
     * paramètre ({@code <streamId>?key=<secret>}).
     */
    private String rtmpUrl = "rtmp://localhost:1935/live";

    /**
     * Base HTTP publique du serveur média pour la lecture HLS
     * (ex. {@code http://localhost:18080} → {@code {base}/live/{id}.m3u8}).
     */
    private String hlsBaseUrl = "http://localhost:18080";

    /**
     * Secret partagé avec le serveur média pour les callbacks ingest
     * ({@code ?secret=...}). DEV-ONLY par défaut : la production l'exige
     * via l'environnement (jamais dans Git, jamais loggé).
     */
    private String ingestSecret = "dev-only-ingest-changeme";

    /** URL de l'API du serveur média (réconciliateur), interne au réseau Docker. */
    private String srsApiUrl = "http://srs:1985/api/v1";

    /** Entropie d'une stream key en octets (256 bits par défaut). */
    private int keyBytes = 32;

    /** Durée de vie d'un marqueur de visionnage sans heartbeat. */
    private Duration viewerTtl = Duration.ofSeconds(90);

    /** Nombre maximal de directs LIVE simultanés par chaîne. */
    private int maxLivePerCreator = 1;

    /** Réconciliateur anti-LIVE-fantôme (poll API SRS, termine les directs orphelins). */
    private boolean reconcilerEnabled = true;

    /**
     * Période du réconciliateur en millisecondes (même convention que
     * {@code app.presence.*} : @Scheduled attend un délai numérique).
     */
    private long reconcilerIntervalMs = 60_000;

    /** Âge minimal d'un LIVE avant de pouvoir être déclaré orphelin. */
    private Duration staleAfter = Duration.ofMinutes(5);

    public String getRtmpUrl() {
        return rtmpUrl;
    }

    public void setRtmpUrl(String rtmpUrl) {
        this.rtmpUrl = rtmpUrl;
    }

    public String getHlsBaseUrl() {
        return hlsBaseUrl;
    }

    public void setHlsBaseUrl(String hlsBaseUrl) {
        this.hlsBaseUrl = hlsBaseUrl;
    }

    public String getIngestSecret() {
        return ingestSecret;
    }

    public void setIngestSecret(String ingestSecret) {
        this.ingestSecret = ingestSecret;
    }

    public String getSrsApiUrl() {
        return srsApiUrl;
    }

    public void setSrsApiUrl(String srsApiUrl) {
        this.srsApiUrl = srsApiUrl;
    }

    public int getKeyBytes() {
        return keyBytes;
    }

    public void setKeyBytes(int keyBytes) {
        this.keyBytes = keyBytes;
    }

    public Duration getViewerTtl() {
        return viewerTtl;
    }

    public void setViewerTtl(Duration viewerTtl) {
        this.viewerTtl = viewerTtl;
    }

    public int getMaxLivePerCreator() {
        return maxLivePerCreator;
    }

    public void setMaxLivePerCreator(int maxLivePerCreator) {
        this.maxLivePerCreator = maxLivePerCreator;
    }

    public boolean isReconcilerEnabled() {
        return reconcilerEnabled;
    }

    public void setReconcilerEnabled(boolean reconcilerEnabled) {
        this.reconcilerEnabled = reconcilerEnabled;
    }

    public long getReconcilerIntervalMs() {
        return reconcilerIntervalMs;
    }

    public void setReconcilerIntervalMs(long reconcilerIntervalMs) {
        this.reconcilerIntervalMs = reconcilerIntervalMs;
    }

    public Duration getStaleAfter() {
        return staleAfter;
    }

    public void setStaleAfter(Duration staleAfter) {
        this.staleAfter = staleAfter;
    }
}
