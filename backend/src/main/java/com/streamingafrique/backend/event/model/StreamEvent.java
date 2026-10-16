package com.streamingafrique.backend.event.model;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.dto.EventEnvelope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Événement temps réel structuré, unité centrale de l'architecture.
 *
 * Immutable et sérialisable en JSON : c'est lui qui circule entre les
 * producteurs (services de domaine), le répartiteur et — sous forme
 * d'{@link EventEnvelope} — jusqu'aux clients WebSocket.
 *
 * Le {@code payload} est volontairement typé {@link Object} : chaque type
 * d'événement porte SON propre DTO (ex. {@code ChatMessagePayload}), ce qui
 * laisse l'architecture ouverte sans la figer sur une forme unique.
 *
 * @param eventId    identifiant unique de l'événement
 * @param type       type d'événement (catalogue {@link EventType})
 * @param occurredAt horodatage UTC de la survenue du fait métier
 * @param payload    donnée métier propre au type (DTO JSON)
 */
public record StreamEvent(UUID eventId, EventType type, Instant occurredAt, Object payload) {

    /** Fabrique canonique : horodate et identifie l'événement maintenant. */
    public static StreamEvent of(EventType type, Object payload) {
        return new StreamEvent(UUID.randomUUID(), Objects.requireNonNull(type, "type"),
                Instant.now(), payload);
    }

    /** Vue client (diffusion WebSocket) de cet événement. */
    public EventEnvelope toEnvelope() {
        return EventEnvelope.from(this);
    }
}