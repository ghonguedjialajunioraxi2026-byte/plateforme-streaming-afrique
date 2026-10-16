package com.streamingafrique.backend.presence;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Registre en mémoire des sessions WebSocket authentifiées : session STOMP →
 * identifiant utilisateur.
 *
 * Il sert au cycle de vie de la présence (retrouver l'utilisateur d'une
 * déconnexion) et au heartbeat (prolonger le TTL des utilisateurs réellement
 * actifs sur CETTE instance).
 *
 * ⚠️ Évolutions prévues pour le multi-instance : ce registre passera dans
 * Redis (clé {@code presence:session:{sessionId}} à TTL court) pour rester
 * cohérent quand plusieurs backends répartissent les connexions.
 */
@Component
public class PresenceSessionRegistry {

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    /** Associe une session STOMP à son utilisateur. */
    public void put(String sessionId, Long userId) {
        sessions.put(sessionId, userId);
    }

    /** Retourne (et retire) l'utilisateur lié à une session fermée. */
    public Long remove(String sessionId) {
        return sessions.remove(sessionId);
    }

    /** Identifiants utilisateurs actifs sur cette instance (dédoublonnés). */
    public Set<Long> activeUserIds() {
        return Set.copyOf(sessions.values());
    }

    /** Vue de debug (nombre de sessions suivies). */
    public Collection<String> sessionIds() {
        return Set.copyOf(sessions.keySet());
    }
}