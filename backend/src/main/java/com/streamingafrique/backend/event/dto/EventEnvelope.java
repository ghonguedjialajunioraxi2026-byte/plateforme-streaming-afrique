package com.streamingafrique.backend.event.dto;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Enveloppe JSON reçue par les clients WebSocket abonnés au topic d'un type
 * d'événement (ex. {@code /topic/events/chat}).
 *
 * Contrat minimal et stable pour les consommateurs : identifiant, type,
 * horodatage, puis le {@code payload} métier (propres à chaque type). Quoi
 * qu'il arrive, un abonné sait d'où vient l'événement et quand il a eu lieu.
 *
 * @param eventId    identifiant unique de l'événement
 * @param type       type d'événement ({@link EventType})
 * @param occurredAt horodatage UTC de la survenue
 * @param payload    donnée métier (DTO JSON propre au type)
 */
public record EventEnvelope(
        UUID eventId,
        EventType type,
        Instant occurredAt,
        Object payload
) {

    public static EventEnvelope from(StreamEvent event) {
        return new EventEnvelope(event.eventId(), event.type(), event.occurredAt(), event.payload());
    }
}