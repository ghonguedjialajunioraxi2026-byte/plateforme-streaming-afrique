package com.streamingafrique.backend.event.dto;

import java.time.Instant;

/**
 * Payload d'exemple pour l'événement {@code CHAT_MESSAGE} — et support de
 * l'événement FICTIF utilisé pour prouver l'architecture (étape 6.22).
 *
 * Ceci n'est PAS un système de chat : aucun historique, salon, modération ou
 * persistance. C'est simplement la forme de message la plus conversationnelle
 * possible pour montrer le chemin d'un événement de bout en bout.
 *
 * @param streamId   identifiant du direct concerné
 * @param authorId   identifiant de l'auteur
 * @param authorName nom d'affichage de l'auteur
 * @param text       contenu du message
 * @param sentAt     horodatage UTC de l'émission
 */
public record ChatMessagePayload(
        long streamId,
        long authorId,
        String authorName,
        String text,
        Instant sentAt
) {
}