package com.streamingafrique.backend.websocket.dto;

import java.time.Instant;

/**
 * Réponse de l'endpoint de démonstration {@code /topic/demo/echo} :
 * l'écho renvoyé au client avec l'horodatage serveur et l'expéditeur.
 */
public record DemoMessage(String text, String sender, Instant serverTime) {
}