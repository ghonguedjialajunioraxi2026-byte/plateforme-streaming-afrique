package com.streamingafrique.backend.presence.dto;

import com.streamingafrique.backend.presence.model.UserPresenceStatus;

import java.time.Instant;

/**
 * Présence d'un utilisateur : portée par les broadcasts WebSocket
 * ({@code /topic/presence}) ET par les réponses REST sur la présence.
 */
public record UserPresenceDto(
        Long userId,
        UserPresenceStatus status,
        Instant lastSeenAt
) {

    /** Fabrique offline : aucun marqueur Redis = utilisé par défaut. */
    public static UserPresenceDto offline(Long userId) {
        return new UserPresenceDto(userId, UserPresenceStatus.OFFLINE, null);
    }
}