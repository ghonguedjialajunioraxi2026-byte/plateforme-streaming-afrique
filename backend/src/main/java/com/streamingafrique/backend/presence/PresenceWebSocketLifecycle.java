package com.streamingafrique.backend.presence;

import com.streamingafrique.backend.presence.service.PresenceService;
import com.streamingafrique.backend.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Intercepte les trames STOMP sur le canal entrant du serveur pour revendiquer
 * l'identité de l'utilisateur à la connexion (CONNECT) et la libérer à la
 * déconnexion (DISCONNECT). C'est un point d'entrée fiable et documenté de la
 * présence : l'intercepteur est synchrone, voit chaque trame du client AVANT le
 * routage vers le broker ou les contrôleurs @MessageMapping.
 *
 * Cycle de vie assuré par cet intercepteur :
 *   - CONNECT    → en-tête {@code userId} lu, utilisateur validé (PostgreSQL),
 *                  registre rempli, marqueur Redis + broadcast ONLINE ;
 *   - DISCONNECT → registre vidé, marqueur nettoyé (dernière session),
 *                  broadcast OFFLINE.
 *
 * Identité PROVISOIRE : tant que l'authentification JWT n'arrive pas, le client
 * annonce son {@code userId} dans les en-têtes du CONNECT STOMP (comme le fera
 * un jeton plus tard). Une connexion anonyme (sans userId) reste utilisable
 * pour les topics ouverts, sans incidence sur la présence.
 *
 * Fermeture brutale : si la WebSocket se ferme sans trame DISCONNECT (onglet
 * fermé, réseau perdu), la session quitte le registre sans broadcast — le
 * cœur de la présence la rattrape : plus aucun heartbeat ne prolonge le
 * marqueur Redis, le TTL expire et le sweep diffuse OFFLINE. Aucune présence
 * n'est donc jamais définitive.
 */
@Component
public class PresenceWebSocketLifecycle implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PresenceWebSocketLifecycle.class);

    private final PresenceSessionRegistry registry;
    private final PresenceService presenceService;
    private final UserRepository userRepository;

    public PresenceWebSocketLifecycle(PresenceSessionRegistry registry,
                                      PresenceService presenceService,
                                      UserRepository userRepository) {
        this.registry = registry;
        this.presenceService = presenceService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        switch (command) {
            case CONNECT -> handleConnect(accessor, sessionId);
            case DISCONNECT -> handleDisconnect(sessionId);
            default -> { /* SUBSCRIBE / SEND / UNSUBSCRIBE : rien pour la présence */ }
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor, String sessionId) {
        Long userId = parseUserId(accessor.getFirstNativeHeader("userId"));
        if (userId == null) {
            return;
        }
        if (!userRepository.existsById(userId)) {
            log.debug("Connexion WebSocket ignorée pour la présence : utilisateur {} introuvable", userId);
            return;
        }
        registry.put(sessionId, userId);
        presenceService.markOnline(userId);
    }

    private void handleDisconnect(String sessionId) {
        Long userId = registry.remove(sessionId);
        if (userId != null) {
            presenceService.markOffline(userId);
        }
    }

    private Long parseUserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}