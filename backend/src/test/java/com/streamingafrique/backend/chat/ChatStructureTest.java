package com.streamingafrique.backend.chat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.18 : persistance du chat — salon = stream, messages
 * utilisateurs + système, masquage modération, historique ordonné (H2,
 * transaction annulée). Aucun WebSocket développé ici.
 */
@SpringBootTest
@Transactional
class ChatStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private ChatMessageRepository messages;

    private Stream salon(String slug) {
        User owner = users.save(User.create(slug + "@example.com", slug, "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(owner, slug + " TV", slug));
        return streams.save(Stream.schedule(channel, "Live " + slug, null, Instant.now()));
    }

    @Test
    void message_utilisateur_defauts_visibles() {
        Stream salon = salon("chatsalon1");
        User ada = users.save(User.create("chateuse@example.com", "chateuse", "hash"));

        ChatMessage message = messages.save(
                ChatMessage.userMessage(salon, ada, "Ada", "Salut le live !", "Salut le live !"));

        assertEquals(ChatMessage.Status.VISIBLE, message.getStatus());
        assertFalse(message.isSystem());
        assertEquals("Ada", message.getAuthorNameSnapshot());
        assertNull(message.getHiddenReason());
        assertEquals(1, messages.countByStreamId(salon.getId()));
    }

    @Test
    void message_systeme_sans_auteur() {
        Stream salon = salon("chatsalon2");
        ChatMessage annonce = messages.save(ChatMessage.systemMessage(salon, "Le direct commence"));

        assertTrue(annonce.isSystem());
        assertNull(annonce.getAuthor());
        assertEquals("Système", annonce.getAuthorNameSnapshot());
    }

    @Test
    void message_vide_ou_sans_auteur_refuse() {
        Stream salon = salon("chatsalon3");
        User ada = users.save(User.create("muette@example.com", "muette", "hash"));

        assertThrows(IllegalArgumentException.class, () ->
                ChatMessage.userMessage(salon, ada, "Ada", "   ", "   "));
        assertThrows(IllegalArgumentException.class, () ->
                ChatMessage.userMessage(salon, null, "X", "Bonjour", "Bonjour"));
    }

    @Test
    void masquage_moderation_avec_motif_conserve() {
        Stream salon = salon("chatsalon4");
        User troll = users.save(User.create("troll@example.com", "troll", "hash"));
        ChatMessage message = messages.save(
                ChatMessage.userMessage(salon, troll, "Troll", "spam", "spam"));

        assertThrows(IllegalArgumentException.class, () -> message.hide("  "));
        message.hide("Spam");
        messages.saveAndFlush(message);

        ChatMessage lu = messages.findById(message.getId()).orElseThrow();
        assertEquals(ChatMessage.Status.HIDDEN, lu.getStatus());
        assertEquals("Spam", lu.getHiddenReason());
        assertEquals("spam", lu.getBodyRaw());
    }

    @Test
    void historique_ordonne_isole_par_salon() {
        Stream salonA = salon("chatordoA");
        Stream salonB = salon("chatordoB");
        User ada = users.save(User.create("ordo@example.com", "ordo", "hash"));

        messages.save(ChatMessage.userMessage(salonA, ada, "Ada", "un", "un"));
        messages.save(ChatMessage.userMessage(salonA, ada, "Ada", "deux", "deux"));
        messages.save(ChatMessage.userMessage(salonB, ada, "Ada", "ailleurs", "ailleurs"));

        var page = messages.findByStreamIdOrderByIdAsc(salonA.getId(), PageRequest.of(0, 10));
        assertEquals(2, page.getContent().size());
        assertEquals("un", page.getContent().get(0).getBodyDisplay());
        assertEquals("deux", page.getContent().get(1).getBodyDisplay());

        var rattrapage = messages.findByStreamIdAndIdGreaterThanOrderByIdAsc(
                salonA.getId(), page.getContent().get(0).getId(), PageRequest.of(0, 10));
        assertEquals(1, rattrapage.size());
    }
}
