package com.streamingafrique.backend.event.controller;

import com.streamingafrique.backend.event.dto.PublishEventRequest;
import com.streamingafrique.backend.event.service.StreamEventPublisher;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Entrée STOMP d'injection d'un événement de DÉMONSTRATION / de test.
 *
 * Le client envoie la même requête que REST via une trame {@code SEND} vers
 * {@code /app/events/publish} ; le serveur la délègue au publisher, qui diffuse
 * l'événement aux abonnés du topic de son type (ex. {@code /topic/events/chat-message}).
 *
 * Contrôleur volontairement dépouillé : toute la logique vit dans le publisher
 * et ses traitants — c'est la brique « envoyer un événement » du temps réel.
 *
 * Hors documentation OpenAPI (STOMP, pas REST) : voir POST /api/events/publish.
 */
@Hidden
@Controller
public class StreamEventMessageController {

    private final StreamEventPublisher publisher;

    public StreamEventMessageController(StreamEventPublisher publisher) {
        this.publisher = publisher;
    }

    @MessageMapping("/events/publish")
    public void publish(@Payload PublishEventRequest request) {
        publisher.publish(request.type(), request.payload());
    }
}