package com.streamingafrique.backend.auth.dto;

import java.time.Instant;

import com.streamingafrique.backend.auth.model.AuthSession;

/**
 * Session/appareil (étape 08) — GET /api/auth/sessions.
 * Juste de quoi reconnaître « cet appareil » : jamais de token, jamais de hash.
 */
public record SessionDto(
        Long id,
        String deviceInfo,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt
) {
    public static SessionDto of(AuthSession session) {
        return new SessionDto(session.getId(), session.getDeviceInfo(), session.getIpAddress(),
                session.getCreatedAt(), session.getLastUsedAt(), session.getExpiresAt());
    }
}
