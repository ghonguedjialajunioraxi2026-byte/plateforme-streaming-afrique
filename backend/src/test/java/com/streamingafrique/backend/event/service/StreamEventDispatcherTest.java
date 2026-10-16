package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du répartiteur : routage par type, auto-découverte via les
 * beans (simulés ici par la liste du constructeur), enregistrement à chaud et
 * résilience (un traitant qui échoue ne bloque pas les suivants).
 */
class StreamEventDispatcherTest {

    @Test
    @DisplayName("Routage : chaque événement part unique aux traitants de son type")
    void route_par_type() {
        RecordingHandler chat = new RecordingHandler(EventType.CHAT_MESSAGE, EventType.NOTIFICATION_CREATED);
        RecordingHandler viewers = new RecordingHandler(EventType.VIEWER_JOINED);
        StreamEventDispatcher dispatcher = new StreamEventDispatcher(List.of(chat, viewers));

        dispatcher.dispatch(StreamEvent.of(EventType.CHAT_MESSAGE, "bonjour"));
        dispatcher.dispatch(StreamEvent.of(EventType.VIEWER_JOINED, "123"));
        dispatcher.dispatch(StreamEvent.of(EventType.STREAM_STARTED, "abc"));

        assertThat(chat.received).extracting(StreamEvent::type)
                .containsExactly(EventType.CHAT_MESSAGE);
        assertThat(viewers.received).extracting(StreamEvent::type)
                .containsExactly(EventType.VIEWER_JOINED);
    }

    @Test
    @DisplayName("Enregistrement à chaud : un traitant ajouté en cours d'exécution est écouté")
    void enregistrer_a_chaud() {
        StreamEventDispatcher dispatcher = new StreamEventDispatcher(List.of());
        RecordingHandler follow = new RecordingHandler(EventType.FOLLOW_CREATED);

        dispatcher.register(follow);

        dispatcher.dispatch(StreamEvent.of(EventType.FOLLOW_CREATED, new Object()));

        assertThat(follow.received).hasSize(1);
    }

    @Test
    @DisplayName("Résilience : un traitant en échec ne bloque pas les suivants du même type")
    void echec_du_premier_ne_bloque_pas_le_suivant() {
        RecordingHandler fail = new RecordingHandler(EventType.CHAT_MESSAGE) {
            @Override
            public void handle(StreamEvent event) {
                super.handle(event);
                throw new IllegalStateException("panne simulée");
            }
        };
        RecordingHandler after = new RecordingHandler(EventType.CHAT_MESSAGE);
        StreamEventDispatcher dispatcher = new StreamEventDispatcher(List.of(fail, after));

        dispatcher.dispatch(StreamEvent.of(EventType.CHAT_MESSAGE, "x"));

        assertThat(fail.received).hasSize(1);
        assertThat(after.received).hasSize(1);
    }

    @Test
    @DisplayName("Aucun traitant n'écoute : l'événement est simplement perdu, sans erreur")
    void aucun_traitant() {
        StreamEventDispatcher dispatcher = new StreamEventDispatcher(List.of());

        dispatcher.dispatch(StreamEvent.of(EventType.CHAT_MESSAGE, "x"));
        // pas d'exception → le test passe
    }

    /** Traitant qui mémorise les événements reçus. */
    private static class RecordingHandler implements StreamEventHandler {

        private final Set<EventType> types;
        private final List<StreamEvent> received = new ArrayList<>();

        private RecordingHandler(EventType... types) {
            this.types = EnumSet.of(types[0], types);
        }

        @Override
        public Set<EventType> handles() {
            return types;
        }

        @Override
        public void handle(StreamEvent event) {
            received.add(event);
        }
    }
}