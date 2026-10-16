package com.streamingafrique.backend.presence.service;

import com.streamingafrique.backend.presence.PresenceProperties;
import com.streamingafrique.backend.presence.dto.UserPresenceDto;
import com.streamingafrique.backend.presence.model.UserPresence;
import com.streamingafrique.backend.presence.model.UserPresenceStatus;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Présence utilisateur reposant sur Redis.
 *
 * Deux structures complémentaires, toutes deux préfixées par l'espace de
 * noms applicatif (voir {@link RedisCacheService}) :
 *
 *   - un MARQUEUR par utilisateur : clé {@code presence:user:{id}}, état
 *     {@link UserPresence} en JSON, avec un TTL ({@code app.presence.ttl}).
 *     Il est « touché » (TTL prolongé + lastSeenAt) tant que des sessions
 *     sont ouvertes. S'il expire, l'utilisateur repasse OFFLINE automatiquement.
 *   - un ENSEMBLE {@code presence:online} : liste « intention » des
 *     utilisateurs censés être en ligne. Sans TTL, il survit aux crashs et
 *     permet au nettoyage ({@link #sweep()}) de diffuser les passages OFFLINE
 *     même quand client a disparu sans déconnexion propre.
 *
 * PRINCIPE « jamais définitif » :
 *   - connexion   → marqueur créé + membre de l'ensemble, ONLINE diffusé ;
 *   - déconnexion → marqueur supprimé (dernière session) + membre retiré,
 *                   OFFLINE diffusé ;
 *   - expiration  → le TTL efface le marqueur → les lectures donnent OFFLINE ;
 *   - nettoyage   → balayage régulier retire de l'ensemble les marqueurs
 *                   morts/figés et diffuse leur OFFLINE.
 *
 * TOUT est best-effort (conventions de {@link RedisCacheService}) : une panne
 * Redis rend les lectures « OFFLINE » sans planter l'application. L'identité
 * reste validée sur PostgreSQL (UserRepository) à la connexion WebSocket, et
 * Redis n'est ici qu'une donne éphémère, jamais une source de vérité durable.
 */
@Service
public class RedisPresenceService implements PresenceService {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceService.class);

    static final String PRESENCE_TOPIC = "/topic/presence";

    private final RedisCacheService cache;
    private final SimpMessagingTemplate messagingTemplate;
    private final Duration ttl;

    /**
     * Le {@link SimpMessagingTemplate} est injecté en {@link Lazy} : le broker
     * STOMP (qui fournit ce template) dépend des configurateurs WebSocket, dont
     * celui qui installe l'intercepteur de présence — un proxy différé évite la
     * dépendance circulaire au démarrage.
     */
    public RedisPresenceService(RedisCacheService cache,
                                @Lazy SimpMessagingTemplate messagingTemplate,
                                PresenceProperties properties) {
        this.cache = cache;
        this.messagingTemplate = messagingTemplate;
        this.ttl = properties.getTtl();
    }

    /* ─── Connexion / déconnexion ────────────────────────────── */

    @Override
    public void markOnline(Long userId) {
        UserPresence existing = readMarker(userId);
        int sessions = (existing != null && existing.sessionCount() != null)
                ? Math.max(0, existing.sessionCount()) + 1
                : 1;
        UserPresence presence = UserPresence.online(userId, sessions);
        saveMarker(presence);
        cache.sadd(CacheKeys.PRESENCE_ONLINE_SET, String.valueOf(userId));
        broadcast(toDto(presence));
    }

    @Override
    public void markOffline(Long userId) {
        UserPresence presence = readMarker(userId);
        if (presence == null) {
            return;
        }
        int sessions = Math.max(0, presence.sessionCount() - 1);
        if (sessions == 0) {
            cache.evict(CacheKeys.presenceUser(userId));
            cache.srem(CacheKeys.PRESENCE_ONLINE_SET, String.valueOf(userId));
            broadcast(UserPresenceDto.offline(userId));
        } else {
            saveMarker(UserPresence.online(userId, sessions));
            touch(userId);
        }
    }

    /* ─── Heartbeat (prolongation) ───────────────────────────── */

    @Override
    public void touch(Long userId) {
        UserPresence presence = readMarker(userId);
        if (presence == null) {
            return;
        }
        saveMarker(UserPresence.online(userId,
                presence.sessionCount() == null ? 0 : presence.sessionCount(), Instant.now()));
    }

    /* ─── Lectures ───────────────────────────────────────────── */

    @Override
    public UserPresenceDto userPresence(Long userId) {
        UserPresence presence = readMarker(userId);
        return presence != null ? toDto(presence) : UserPresenceDto.offline(userId);
    }

    @Override
    public UserPresenceStatus statusOf(Long userId) {
        return userPresence(userId).status();
    }

    @Override
    public boolean isOnline(Long userId) {
        return statusOf(userId) == UserPresenceStatus.ONLINE;
    }

    @Override
    public long countOnline() {
        return onlineUserIds().size();
    }

    /**
     * Les membres de l'ensemble « online » sont filtrés sur l'existence d'un
     * marqueur vivant : un membre dont le TTL a expiré ne compte pas (c'est
     * justement pour cela que la présence n'est jamais définitive).
     */
    @Override
    public List<Long> onlineUserIds() {
        Set<String> members = cache.smembers(CacheKeys.PRESENCE_ONLINE_SET);
        return members.stream()
                .map(this::parseId)
                .filter(Objects::nonNull)
                .filter(userId -> cache.exists(CacheKeys.presenceUser(userId)))
                .distinct()
                .sorted()
                .toList();
    }

    /* ─── Nettoyage des présences obsolètes ──────────────────── */

    @Override
    public long sweep() {
        Set<String> members = cache.smembers(CacheKeys.PRESENCE_ONLINE_SET);
        long cleaned = 0;
        for (String member : members) {
            Long userId = parseId(member);
            if (userId == null) {
                continue;
            }
            UserPresence presence = readMarker(userId);
            if (presence == null || isStale(presence)) {
                cache.evict(CacheKeys.presenceUser(userId));
                cache.srem(CacheKeys.PRESENCE_ONLINE_SET, member);
                broadcast(UserPresenceDto.offline(userId));
                cleaned++;
                log.info("Présence nettoyée (obsolète) : utilisateur {}, marqueur {}",
                        userId, presence == null ? "expiré" : "figé");
            }
        }
        return cleaned;
    }

    /* ─── Helpers ────────────────────────────────────────────── */

    private UserPresence readMarker(Long userId) {
        Object value = cache.getJson(CacheKeys.presenceUser(userId));
        return value instanceof UserPresence presence ? presence : null;
    }

    private void saveMarker(UserPresence presence) {
        cache.putJson(CacheKeys.presenceUser(presence.userId()), presence, ttl);
    }

    private UserPresenceDto toDto(UserPresence presence) {
        return new UserPresenceDto(presence.userId(), presence.status(), presence.lastSeenAt());
    }

    /** Marqueur « figé » : dernière activité au-delà du TTL (session fantôme). */
    private boolean isStale(UserPresence presence) {
        Instant lastSeen = presence.lastSeenAt();
        return lastSeen == null || Instant.now().isAfter(lastSeen.plus(ttl));
    }

    private void broadcast(UserPresenceDto dto) {
        messagingTemplate.convertAndSend(PRESENCE_TOPIC, dto);
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}