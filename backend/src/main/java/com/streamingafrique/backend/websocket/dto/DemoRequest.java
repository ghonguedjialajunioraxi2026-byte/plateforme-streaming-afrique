package com.streamingafrique.backend.websocket.dto;

/**
 * Corps d'un message envoyé par un client vers {@code /app/echo}
 * (démonstration de l'aller-retour client → serveur → client).
 */
public record DemoRequest(String text) {
}