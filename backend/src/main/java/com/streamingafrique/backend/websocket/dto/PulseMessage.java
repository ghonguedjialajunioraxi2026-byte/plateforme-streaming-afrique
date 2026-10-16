package com.streamingafrique.backend.websocket.dto;

import java.time.Instant;

/**
 * Battement de cœur du temps réel, diffusé périodiquement sur {@code /topic/pulse}.
 * Prouve au client qu'il est bien abonné et connecté, et transporte le nombre
 * de clients connectés à l'instant T (démo de ce que fournira la présence).
 */
public record PulseMessage(long sequence, int connectedClients, Instant serverTime) {
}