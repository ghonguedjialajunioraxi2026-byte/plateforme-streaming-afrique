package com.streamingafrique.backend.stream.service;

import java.time.Duration;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.stream.config.StreamingProperties;

/**
 * Audience instantanée des directs (étape 10, PARTIE 19).
 *
 * Pourquoi pas le serveur média : HLS = fichiers statiques servis en HTTP,
 * le serveur ne voit pas de « sessions viewers » (les hooks on_play/on_stop
 * ne couvrent que RTMP/WebRTC). Le frontend pulse donc toutes les ~30 s
 * ({@code POST /api/streams/{id}/heartbeat}) avec un jeton anonyme
 * (sessionStorage) ; chaque jeton alimente un HyperLogLog Redis
 * (comptage approximatif, mémoire constante, TTL rafraîchi).
 *
 * PostgreSQL reste la source de l'HISTORIQUE : seul le pic y est flushé
 * (fin de live). Tout est best-effort : Redis absent → compteurs à 0,
 * jamais d'erreur.
 */
@Service
public class ViewerService {

    private static final Logger log = LoggerFactory.getLogger(ViewerService.class);

    /** Jetons acceptés : 1 à 64 car. alphanumériques + tirets (jamais de membre géant). */
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final RedisCacheService cache;
    private final Duration viewerTtl;

    public ViewerService(RedisCacheService cache, StreamingProperties properties) {
        this.cache = cache;
        this.viewerTtl = properties.getViewerTtl();
    }

    /**
     * Enregistre une pulsation et renvoie l'audience estimée.
     *
     * @return viewers estimés (pic Redis mis à jour au passage si dépassé)
     */
    public long heartbeat(Long streamId, String viewerToken) {
        if (viewerToken == null || !TOKEN.matcher(viewerToken).matches()) {
            throw new IllegalArgumentException("Jeton de visionnage invalide");
        }
        String viewersKey = CacheKeys.streamViewers(streamId);
        cache.pfadd(viewersKey, viewerToken);
        cache.expire(viewersKey, viewerTtl);
        long viewers = cache.pfcount(viewersKey);
        updatePeak(streamId, viewers);
        return viewers;
    }

    /** Audience estimée en ce moment (0 si inconnue — jamais d'erreur). */
    public long currentViewers(Long streamId) {
        return cache.pfcount(CacheKeys.streamViewers(streamId));
    }

    /** Pic observé côté Redis (miroir, flushé en base à la fin du live). */
    public long redisPeak(Long streamId) {
        String raw = cache.get(CacheKeys.streamViewersPeak(streamId));
        if (raw == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(raw));
        } catch (NumberFormatException e) {
            log.warn("Pic viewers Redis illisible pour le direct {}", streamId);
            return 0L;
        }
    }

    /**
     * Lit le pic Redis, supprime les clés éphémères du direct et renvoie
     * le pic (à persister via {@code recordPeakViewers}).
     */
    public long takePeak(Long streamId) {
        long peak = Math.max(redisPeak(streamId), currentViewers(streamId));
        cache.evict(CacheKeys.streamViewersPeak(streamId));
        cache.evict(CacheKeys.streamViewers(streamId));
        return peak;
    }

    private void updatePeak(Long streamId, long viewers) {
        String peakKey = CacheKeys.streamViewersPeak(streamId);
        String raw = cache.get(peakKey);
        long peak = 0L;
        if (raw != null) {
            try {
                peak = Long.parseLong(raw);
            } catch (NumberFormatException e) {
                peak = 0L;
            }
        }
        if (viewers > peak) {
            cache.put(peakKey, Long.toString(viewers), viewerTtl.multipliedBy(10));
        } else {
            cache.expire(peakKey, viewerTtl.multipliedBy(10));
        }
    }
}
