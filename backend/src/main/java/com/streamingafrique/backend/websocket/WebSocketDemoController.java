package com.streamingafrique.backend.websocket;

import com.streamingafrique.backend.websocket.dto.DemoMessage;
import com.streamingafrique.backend.websocket.dto.DemoRequest;

import java.time.Instant;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Endpoints de DÉMONSTRATION du temps réel.
 *
 * ⚠️ Ce n'est PAS le chat : il n'y a ni historique, ni salons, ni utilisateurs.
 * Ça prouve simplement que la messagerie STOMP fonctionne de bout en bout.
 *
 *  - {@code /app/echo} → {@code /topic/demo/echo} : aller-retour
 *    client → serveur → client (la base du temps réel).
 *  - À supprimer ou étoffer quand les vraies fonctionnalités arriveront.
 *
 * Hors documentation OpenAPI (STOMP, pas REST).
 */
@Hidden
@Controller
public class WebSocketDemoController {

    /**
     * Renvoie le texte reçu avec l'identifiant de session et l'heure serveur.
     *
     * 1. Le client envoie un message STOMP vers /app/echo (préfixe applicatif).
     * 2. Spring le route ici (le préfixe /app est retiré).
     * 3. La valeur renvoyée est publiée sur @SendTo → tous les abonnés
     *    de /topic/demo/echo (dont l'expéditeur) la reçoivent.
     *
     * @Header("simpSessionId") : identifiant STOMP unique de la connexion
     * (equivaut à l'expéditeur tant qu'il n'y a pas d'authentification).
     */
    @MessageMapping("/echo")
    @SendTo("/topic/demo/echo")
    public DemoMessage echo(DemoRequest request, @Header("simpSessionId") String sessionId) {
        return new DemoMessage(request.text(), sessionId, Instant.now());
    }
}