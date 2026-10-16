package com.streamingafrique.backend.chat.service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;
import com.streamingafrique.backend.event.service.StreamEventHandler;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Persistance du chat live (FINALISATION) : chaque {@code CHAT_MESSAGE}
 * diffusé est inséré dans {@code chat_messages} AVANT broadcast effectif —
 * le traitant WebSocket reste indépendant (le répartiteur isole les erreurs :
 * un échec ici ne coupe jamais le direct).
 *
 * Règles :
 * - auteur = compte AUTHENTIFIÉ serveur (jamais l'{@code authorId} déclaratif
 *   du payload, falsifiable) ; sans authentification → diffusion seule ;
 * - texte assaini (balises retirées), 1..2000 caractères, sinon ignoré ;
 * - direct inexistant → ignoré (log debug).
 */
@Component
public class ChatPersistHandler implements StreamEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistHandler.class);
    private static final int MAX_LENGTH = 2000;

    private final ChatMessageRepository messages;
    private final StreamRepository streams;
    private final UserRepository users;

    public ChatPersistHandler(ChatMessageRepository messages,
                              StreamRepository streams,
                              UserRepository users) {
        this.messages = messages;
        this.streams = streams;
        this.users = users;
    }

    @Override
    public Set<EventType> handles() {
        return EnumSet.of(EventType.CHAT_MESSAGE);
    }

    @Override
    @Transactional
    public void handle(StreamEvent event) {
        Object payload = event.payload();
        Long streamId = extractStreamId(payload);
        String text = extractText(payload);
        if (streamId == null || text == null) {
            return;
        }
        var stream = streams.findById(streamId).orElse(null);
        if (stream == null) {
            log.debug("Chat ignoré : direct {} inexistant", streamId);
            return;
        }
        User author = authenticatedAuthor();
        if (author == null) {
            // Démo / STOMP anonyme : diffusion seule, pas de persistance.
            return;
        }
        String snapshot = author.getDisplayName() != null && !author.getDisplayName().isBlank()
                ? author.getDisplayName()
                : author.getUsername();
        messages.save(ChatMessage.userMessage(stream, author, snapshot, text, sanitize(text)));
    }

    private User authenticatedAuthor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser actor = CurrentUser.from(authentication);
        if (actor == null || !actor.isAuthenticated()) {
            return null;
        }
        return users.findById(actor.id()).orElse(null);
    }

    static Long extractStreamId(Object payload) {
        Object raw = field(payload, "streamId");
        if (raw instanceof Number number) {
            long value = number.longValue();
            return value > 0 ? value : null;
        }
        return null;
    }

    static String extractText(Object payload) {
        Object raw = field(payload, "text");
        if (raw == null) {
            raw = field(payload, "message");
        }
        if (!(raw instanceof String text)) {
            return null;
        }
        String clean = text.trim();
        if (clean.isEmpty()) {
            return null;
        }
        String display = sanitize(clean);
        if (display.isEmpty()) {
            return null;
        }
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) : clean;
    }

    static String sanitize(String text) {
        return text.replaceAll("<[^>]*>", "").trim();
    }

    @SuppressWarnings("unchecked")
    private static Object field(Object payload, String name) {
        if (payload instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            var method = payload.getClass().getMethod(name);
            return method.invoke(payload);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
