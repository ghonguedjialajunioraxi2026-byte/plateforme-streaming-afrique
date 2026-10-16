package com.streamingafrique.backend.auth.dto;

/**
 * Paire de jetons remise au login / refresh (étape 08).
 *
 * Access court (15 min) présenté en {@code Authorization: Bearer} ;
 * refresh long (7 j) conservé par le client pour renouveler — JAMAIS les
 * deux au même endroit sensible côté frontend (voir doc frontend).
 * Durées en secondes (même convention que {@code LoginResponse.expiresIn}).
 */
public record AuthTokens(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {}
