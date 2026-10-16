package com.streamingafrique.backend.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.streamingafrique.backend.config.RedisCacheProperties;

/**
 * Compteur anti-abus à fenêtre fixe, adossé à Redis (étape 6.27).
 *
 * Principe : clé {@code <prefix>:ratelimit:<bucket>:<identifiant>} incrémentée
 * à chaque requête, avec expiration = fenêtre. Si le compteur dépasse la
 * limite → refus (HTTP 429). Redis rend la limite GLOBALE à toutes les
 * instances (indispensable dès qu'on scale horizontalement) — un compteur
 * local par instance laisserait passer N × limite.
 *
 * Panne Redis → REPLI MÉMOIRE (mode dégradé mono-instance, même convention
 * que {@code RedisCacheService}) : la protection reste active localement au
 * lieu de tout laisser passer ou de tout bloquer. En cas de doute on laisse
 * passer (fail-open) uniquement si le repli lui-même échoue.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final StringRedisTemplate redis;
    private final String prefix;

    /** Repli mémoire : clé → compteur de fenêtre (protégé par synchronized). */
    private final Map<String, MemoryWindow> memory = new ConcurrentHashMap<>();

    public RateLimitService(StringRedisTemplate redis, RedisCacheProperties properties) {
        this.redis = redis;
        this.prefix = properties.getPrefix();
    }

    /** Résultat d'un contrôle : autorisé ou non + délai avant réessai. */
    public record Verdict(boolean allowed, long retryAfterSeconds) {
    }

    /**
     * Consomme UN jeton du bucket pour {@code identifier}.
     *
     * @param bucket    famille de routes (login, search…)
     * @param identifier client (ex. {@code ip:1.2.3.4} ou {@code user:42})
     * @param limit     requêtes max par fenêtre
     * @param window    durée de la fenêtre
     */
    public Verdict tryConsume(String bucket, String identifier, int limit, Duration window) {
        if (limit <= 0) {
            return new Verdict(false, window.toSeconds());
        }
        String key = prefix + ":ratelimit:" + bucket + ":" + identifier;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return memoryFallback(key, limit, window);
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            if (count > limit) {
                return new Verdict(false, currentTtlSeconds(key, window));
            }
            return new Verdict(true, 0L);
        } catch (Exception ex) {
            log.warn("Redis indisponible pour le rate limiting (clé {}) : {} — repli mémoire",
                    key, ex.getMessage());
            return memoryFallback(key, limit, window);
        }
    }

    /** TTL restant de la fenêtre (repli : durée de la fenêtre, borne haute). */
    private long currentTtlSeconds(String key, Duration window) {
        try {
            Long ttl = redis.getExpire(key);
            if (ttl != null && ttl > 0) {
                return ttl;
            }
        } catch (Exception ex) {
            log.warn("Lecture TTL rate limiting impossible (clé {}) : {}", key, ex.getMessage());
        }
        return Math.max(1L, window.toSeconds());
    }

    /* ─── Repli mémoire ─────────────────────────────────────────── */

    private static final class MemoryWindow {
        long count;
        Instant expiresAt;
    }

    private synchronized Verdict memoryFallback(String key, int limit, Duration window) {
        Instant now = Instant.now();
        MemoryWindow current = memory.get(key);
        if (current == null || !now.isBefore(current.expiresAt)) {
            current = new MemoryWindow();
            current.count = 0;
            current.expiresAt = now.plus(window);
            memory.put(key, current);
        }
        current.count++;
        if (current.count > limit) {
            long retry = Math.max(1L, Duration.between(now, current.expiresAt).toSeconds());
            return new Verdict(false, retry);
        }
        return new Verdict(true, 0L);
    }
}
