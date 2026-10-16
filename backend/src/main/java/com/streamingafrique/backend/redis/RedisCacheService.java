package com.streamingafrique.backend.redis;

import java.time.Duration;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.streamingafrique.backend.config.RedisCacheProperties;

/**
 * Service de cache Redis (cache-aside) avec TTL et invalidation.
 *
 * Rôle : accélérer les lectures fréquentes (catégories publiques, listes…
 * tout en laissant la PostgreSQL comme **source de vérité absolue**.
 * En cas de panne Redis, les lectures tombent sur la base sans erreur.
 *
 * Fournit :
 *  - lecture/écriture de chaînes simples (put/get)
 *  - sérialisation JSON d'objets (putJson/getJson) via {@code redisObjectTemplate}
 *  - invalidation par clé ou par pattern (evict/evictByPattern)
 *  - vérification d'existence (exists)
 *
 * TOUTES les opérations sont best-effort : une exception Redis est loguée
 * en WARN et propagée en résultat neutre (null/false/0) — l'application
 * ne plante jamais à cause d'un souci cache.
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate stringTemplate;
    private final RedisTemplate<String, Object> jsonTemplate;
    private final String prefix;
    private final Duration defaultTtl;

    public RedisCacheService(StringRedisTemplate stringTemplate,
                             RedisTemplate<String, Object> jsonTemplate,
                             RedisCacheProperties properties) {
        this.stringTemplate = stringTemplate;
        this.jsonTemplate = jsonTemplate;
        this.prefix = properties.getPrefix();
        this.defaultTtl = properties.getDefaultTtl();
    }

    /* ═══════════════════════════════════════════════════════════
     * Chaînes simples (ex. compteurs, jetons)
     * ═══════════════════════════════════════════════════════════ */

    /** Écrit une valeur String avec le TTL par défaut. */
    public void put(String key, String value) {
        execute(() -> stringTemplate.opsForValue().set(full(key), value, defaultTtl));
    }

    /** Écrit une valeur String avec un TTL explicite. */
    public void put(String key, String value, Duration ttl) {
        execute(() -> stringTemplate.opsForValue().set(full(key), value, ttl));
    }

    /** Lecture par clé (préfixée). */
    public String get(String key) {
        return execute(() -> stringTemplate.opsForValue().get(full(key)));
    }

    /**
     * Pose une clé UNIQUEMENT si absente, avec TTL (étape 15 : garde
     * anti-abus des vues — un viewer = une vue par fenêtre).
     *
     * @return true si la clé a été posée (première vue de la fenêtre),
     *         false si déjà présente OU si Redis est indisponible
     *         (fail-closed : pas de comptage sans garde, sous-comptage
     *         possible en mode dégradé, jamais de gonflement).
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Boolean absent = execute(() -> stringTemplate.opsForValue()
                .setIfAbsent(full(key), value, ttl));
        return Boolean.TRUE.equals(absent);
    }

    /* ═══════════════════════════════════════════════════════════
     * Sérialisation JSON (objets métier)
     * ═══════════════════════════════════════════════════════════ */

    /** Sérialise et stocke un objet en JSON avec le TTL par défaut. */
    public void putJson(String key, Object value) {
        putJson(key, value, defaultTtl);
    }

    /** Sérialise et stocke un objet en JSON avec un TTL explicite. */
    public void putJson(String key, Object value, Duration ttl) {
        execute(() -> jsonTemplate.opsForValue().set(full(key), value, ttl));
    }

    /**
     * Restaure un objet depuis le cache JSON.
     *
     * @return l'objet désérialisé, {@code null} si la clé n'existe pas
     *         ou si la désérialisation échoue (fallback DB garanti).
     */
    public Object getJson(String key) {
        return execute(() -> jsonTemplate.opsForValue().get(full(key)));
    }

    /* ═══════════════════════════════════════════════════════════
     * Invalidation (critique pour la cohérence cache-aside)
     * ═══════════════════════════════════════════════════════════ */

    /** Vérifie l'existence d'une clé préfixée. */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(execute(() -> stringTemplate.hasKey(full(key))));
    }

    /* ═══════════════════════════════════════════════════════════
     * HyperLogLog (comptage approximatif de visiteurs uniques)
     * ═══════════════════════════════════════════════════════════ */

    /**
     * Ajoute des éléments à un HyperLogLog (best-effort).
     *
     * @return true si le compteur a (probablement) changé, false sinon
     *         ou si Redis est indisponible.
     */
    public boolean pfadd(String key, String... values) {
        Long added = execute(() -> stringTemplate.opsForHyperLogLog().add(full(key), values));
        return added != null && added > 0;
    }

    /**
     * Cardinalité approximative d'un HyperLogLog (0 si absent ou erreur).
     */
    public long pfcount(String key) {
        Long size = execute(() -> stringTemplate.opsForHyperLogLog().size(full(key)));
        return size != null ? size : 0L;
    }

    /** Supprime UNE clé (invalide un élément précis). */
    public void evict(String key) {
        execute(() -> stringTemplate.delete(full(key)));
    }

    /**
     * Invalide toutes les clés correspondant au pattern donné.
     *
     * Utile pour les listes paginées : {@code evictByPattern("categories:list:*")}
     * supprime toutes les pages sans les énumérer un par un.
     *
     * ⚠️ La clé doit être préfixée par {@code CacheKeys.*} (domaine),
     * pas par le préfixe applicatif — celui-ci est ajouté automatiquement.
     *
     * @return nombre de clés supprimées
     */
    public long evictByPattern(String pattern) {
        Set<String> keys = execute(() -> stringTemplate.keys(full(pattern)));
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        Long deleted = execute(() -> stringTemplate.delete(keys));
        return deleted != null ? deleted : 0;
    }

    /* ═══════════════════════════════════════════════════════════
     * Ensembles (présence utilisateur, tags…)
     * ═══════════════════════════════════════════════════════════ */

    /** Ajoute des membres à un ensemble Redis → nombre réellement ajoutés. */
    public long sadd(String key, String... values) {
        Long added = execute(() -> stringTemplate.opsForSet().add(full(key), values));
        return added != null ? added : 0L;
    }

    /** Retire des membres d'un ensemble Redis → nombre réellement retirés. */
    public long srem(String key, String... values) {
        Long removed = execute(() -> stringTemplate.opsForSet().remove(full(key), (Object[]) values));
        return removed != null ? removed : 0L;
    }

    /** Lit tous les membres d'un ensemble (vide si absent ou erreur best-effort). */
    public Set<String> smembers(String key) {
        Set<String> members = execute(() -> stringTemplate.opsForSet().members(full(key)));
        return members != null ? members : Set.of();
    }

    /* ═══════════════════════════════════════════════════════════
     * Durée de vie
     * ═══════════════════════════════════════════════════════════ */

    /** Prolonge (ou définit) le TTL d'une clé existante — utile aux compteurs vivants. */
    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(execute(() -> stringTemplate.expire(full(key), ttl)));
    }

    /* ═══════════════════════════════════════════════════════════
     * Connexion
     * ═══════════════════════════════════════════════════════════ */

    /** Vérifie la connexion Redis (PING → PONG). Ne propage pas les exceptions. */
    public boolean ping() {
        try {
            String pong = stringTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            return false;
        }
    }

    /* ═══════════════════════════════════════════════════════════
     * Exécution best-effort : jamais d'exception visible par le caller.
     * ═══════════════════════════════════════════════════════════ */

    private <T> T execute(CacheOperation<T> operation) {
        try {
            return operation.run();
        } catch (Exception e) {
            log.warn("Opération Redis échouée (mode dégradé, fallback DB) : {}",
                    e.getMessage());
            return null;
        }
    }

    private void execute(VoidOperation operation) {
        try {
            operation.run();
        } catch (Exception e) {
            log.warn("Opération Redis échouée (mode dégradé, fallback DB) : {}",
                    e.getMessage());
        }
    }

    private String full(String key) {
        return prefix + ":" + key;
    }

    @FunctionalInterface
    private interface CacheOperation<T> {
        T run() throws Exception;
    }

    @FunctionalInterface
    private interface VoidOperation {
        void run() throws Exception;
    }
}
