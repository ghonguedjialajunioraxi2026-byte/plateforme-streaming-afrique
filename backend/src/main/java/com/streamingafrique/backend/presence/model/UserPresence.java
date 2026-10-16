package com.streamingafrique.backend.presence.model;

import java.time.Instant;

/**
 * État de présence stocké dans Redis pour un utilisateur.
 *
 * Le « marqueur » (clé {@code presence:user:{id}}) porte le statut, la date
 * de dernière activité et le nombre de sessions ouvertes (multi-appareils).
 * Il vit avec un TTL : s'il expire (client disparu sans déconnexion propre),
 * l'utilisateur redevient automatiquement {@code OFFLINE} — aucune présence
 * n'est jamais considérée comme définitive.
 *
 * Record : sérialisable par Jackson (les DTOs de ce projet sont des records).
 */
public record UserPresence(
        Long userId,
        UserPresenceStatus status,
        Instant lastSeenAt,
        Integer sessionCount
) {

    /** Nouvelle présence EN LIGNE pour une première (ou nouvelle) session. */
    public static UserPresence online(Long userId, int sessionCount) {
        return new UserPresence(userId, UserPresenceStatus.ONLINE, Instant.now(), sessionCount);
    }

    /** Identique mais horodatée : utilisée quand on rafraîchit le TTL. */
    public static UserPresence online(Long userId, int sessionCount, Instant lastSeenAt) {
        return new UserPresence(userId, UserPresenceStatus.ONLINE, lastSeenAt, sessionCount);
    }
}