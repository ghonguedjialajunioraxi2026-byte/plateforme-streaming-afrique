package com.streamingafrique.backend.chat.service;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;
import com.streamingafrique.backend.security.AuthenticatedUser;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FINALISATION : chaque {@code CHAT_MESSAGE} authentifié est persisté
 * (historique + modération), avec l'auteur SERVEUR (jamais le déclaratif).
 * Sans authentification : diffusion seule, rien en base.
 */
@SpringBootTest
@Transactional
class ChatPersistHandlerTest {

    @Autowired
    private ChatPersistHandler handler;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private ChatMessageRepository messages;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private Stream salon(String slug) {
        User owner = users.save(User.create(slug + "@example.com", slug, slug + " TV", "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(owner, slug + " TV", slug));
        return streams.save(Stream.schedule(channel, "Live " + slug, null, Instant.now()));
    }

    private void authenticateAs(User user) {
        var principal = AuthenticatedUser.of(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void message_authentifie_persiste_avec_auteur_serveur() {
        Stream salon = salon("persistchat1");
        User ada = users.save(User.create("ada@example.com", "ada", "Ada", "hash"));
        authenticateAs(ada);

        // Payload menteur : authorId d'un autre compte — le serveur impose l'auteur réel.
        User intrus = users.save(User.create("intrus@example.com", "intrus", "Intrus", "hash"));
        handler.handle(StreamEvent.of(EventType.CHAT_MESSAGE,
                Map.of("streamId", salon.getId(), "authorId", intrus.getId(),
                        "authorName", "Intrus", "message", "Salut le live !")));

        assertEquals(1, messages.countByStreamId(salon.getId()));
        ChatMessage saved = messages.findByStreamIdOrderByIdAsc(salon.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent().get(0);
        assertEquals(ada.getId(), saved.getAuthor().getId());
        assertEquals("Ada", saved.getAuthorNameSnapshot());
        assertEquals("Salut le live !", saved.getBodyDisplay());
    }

    @Test
    void sans_authentification_rien_en_base() {
        Stream salon = salon("persistchat2");

        handler.handle(StreamEvent.of(EventType.CHAT_MESSAGE,
                Map.of("streamId", salon.getId(), "message", "diffusion seule")));

        assertEquals(0, messages.countByStreamId(salon.getId()));
    }

    @Test
    void payload_invalide_ou_salon_inconnu_ignore() {
        Stream salon = salon("persistchat3");
        User ada = users.save(User.create("ada3@example.com", "ada3", "Ada", "hash"));
        authenticateAs(ada);

        handler.handle(StreamEvent.of(EventType.CHAT_MESSAGE,
                Map.of("streamId", salon.getId(), "message", "   ")));
        handler.handle(StreamEvent.of(EventType.CHAT_MESSAGE,
                Map.of("streamId", 999999999L, "message", "fantôme")));
        handler.handle(StreamEvent.of(EventType.CHAT_MESSAGE, Map.of("message", "sans salon")));

        assertEquals(0, messages.countByStreamId(salon.getId()));
    }

    @Test
    void extracteurs_tolèrent_map_et_record() {
        assertEquals(7L, ChatPersistHandler.extractStreamId(Map.of("streamId", 7)));
        assertEquals("hello", ChatPersistHandler.extractText(Map.of("message", "  hello  ")));
        assertNull(ChatPersistHandler.extractText(Map.of("message", "   ")));
        assertNull(ChatPersistHandler.extractStreamId(Map.of("streamId", -2)));
    }
}
