package com.streamingafrique.backend.event.controller;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.event.dto.EventEnvelope;
import com.streamingafrique.backend.event.dto.PublishEventRequest;
import com.streamingafrique.backend.event.model.StreamEvent;
import com.streamingafrique.backend.event.service.StreamEventPublisher;
import com.streamingafrique.backend.security.EndpointTier;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Entrée REST d'injection d'un événement de DÉMONSTRATION / de test.
 *
 * Ce contrôleur ne fait que DÉLÉGUER au {@link StreamEventPublisher} : aucune
 * logique d'architecture ici. L'événement part ensuite :
 *   - en diffusion aux abonnés du topic de son type (WebSocket) ;
 *   - à tous les traitants {@code StreamEventHandler} enregistrés.
 *
 * En production, ce ne sont pas des clients qui publient ainsi les événements
 * métier : ce point d'entrée sert la démo et les tests d'intégration.
 *
 * Étape 22 / SEC-003 : publication réservée aux comptes AUTHENTIFIÉS — un
 * anonyme pouvait injecter des événements arbitraires (dont un auteur
 * falsifié) diffusés à tous les abonnés WebSocket du topic.
 */
@Tag(name = "Événements (démo)", description = "Injection d'événements temps réel "
        + "(connecté, démo/tests). L'événement est diffusé aux abonnés WebSocket du topic "
        + "de son type (`/topic/events/<type-en-kebab>`, ex. `/topic/events/chat-message`). "
        + "Types : `STREAM_STARTED|STREAM_ENDED|VIEWER_JOINED|VIEWER_LEFT|CHAT_MESSAGE|"
        + "FOLLOW_CREATED|NOTIFICATION_CREATED`. En production, ce ne sont pas les clients "
        + "qui publient les événements métier.")
@RestController
@RequestMapping("/api/events")
public class StreamEventRestController {

    private final StreamEventPublisher publisher;

    public StreamEventRestController(StreamEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publie l'événement et renvoie l'enveloppe diffusée
     * (identifiant + horodatage réels), pour vérification côté client.
     */
    @Operation(summary = "Publier un événement (démo)",
            description = "Connecté (SEC-003). Exemple : `{\"type\":\"CHAT_MESSAGE\",\"payload\":"
                    + "{\"streamId\":1,\"authorId\":2,\"message\":\"Salut !\"}}`. "
                    + "Réponse : l'enveloppe diffusée (`eventId, type, occurredAt`). "
                    + "Limité anti-spam (famille `messages`, 429 au-delà du quota).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Message de chat",
                            value = "{\"type\": \"CHAT_MESSAGE\", \"payload\": {\"streamId\": 1, "
                                    + "\"authorId\": 2, \"message\": \"Salut !\"}}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`EventEnvelope` diffusée"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Type inconnu ou payload invalide",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "Anti-spam (voir `Retry-After`)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping("/publish")
    public ApiResponse<EventEnvelope> publish(@Valid @RequestBody PublishEventRequest request) {
        StreamEvent event = StreamEvent.of(request.type(), request.payload());
        publisher.publish(event);
        return ApiResponse.success("Événement publié", event.toEnvelope());
    }
}