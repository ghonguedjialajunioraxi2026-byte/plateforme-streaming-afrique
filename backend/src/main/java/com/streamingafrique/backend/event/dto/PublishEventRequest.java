package com.streamingafrique.backend.event.dto;

import com.streamingafrique.backend.event.EventType;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Requête d'injection d'un événement (démonstration / tests).
 *
 * Deux vecteurs d'entrée, même requête :
 *   - REST   : {@code POST /api/events/publish} → le serveur publie l'événement ;
 *   - STOMP  : {@code SEND} vers {@code /app/events/publish} → idem.
 *
 * Le {@code payload} est libre (un objet JSON quelconque) : c'est le DTO métier
 * associé au {@code type}, remonté verbatim dans l'événement. En conditions
 * réelles, ce ne sont pas les clients qui injectent des événements : ce sont
 * les services de domaine (stream, chat, notifications…) qui publient via
 * {@code StreamEventPublisher}.
 *
 * @param type    type d'événement à publier
 * @param payload donnée métier de l'événement
 */
public record PublishEventRequest(
        @NotNull(message = "type est requis")
        @Schema(description = "Type d'événement à publier", example = "CHAT_MESSAGE")
        EventType type,
        @Schema(description = "Donnée métier de l'événement (forme dépend du type)",
                example = "{\"streamId\": 1, \"authorId\": 2, \"text\": \"Salut le live !\"}")
        Object payload) {
}