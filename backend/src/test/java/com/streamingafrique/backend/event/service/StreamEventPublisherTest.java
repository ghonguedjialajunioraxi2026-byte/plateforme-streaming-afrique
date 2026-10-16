package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.StreamEventProperties;
import com.streamingafrique.backend.event.dto.ChatMessagePayload;
import com.streamingafrique.backend.event.dto.EventEnvelope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du chemin de publication vers WebSocket : un événement publié
 * est diffusé sur le topic de son type (enveloppe incluse), et l'interrupteur
 * {@code app.stream-events.enabled=false} coupe UNIQUEMENT la diffusion.
 *
 * Le {@link SimpMessagingTemplate} est falsifié par un enregistreur concret
 * (contourne les surcharges {@code convertAndSend} ambiguës des mocks).
 */
class StreamEventPublisherTest {

    private StreamEventProperties properties;
    private RecordingMessagingTemplate messaging;
    private StandardStreamEventPublisher publisher;

    @BeforeEach
    void setUp() {
        properties = new StreamEventProperties();
        messaging = new RecordingMessagingTemplate();
        TopicEventDestinationResolver resolver = new TopicEventDestinationResolver(properties);
        StreamEventDispatcher dispatcher = new StreamEventDispatcher(List.of(
                new WebSocketStreamEventHandler(messaging, resolver, properties)));
        publisher = new StandardStreamEventPublisher(dispatcher);
    }

    @Test
    @DisplayName("Un événement publié est diffusé sur /topic/events/{slug} avec une enveloppe complète")
    void publie_diffuse_sur_le_topic_du_type() {
        ChatMessagePayload payload = new ChatMessagePayload(7, 3, "Ada", "Bonjour", Instant.now());

        publisher.publish(EventType.CHAT_MESSAGE, payload);

        assertThat(messaging.sent).hasSize(1);
        Object[] sent = messaging.sent.get(0);
        assertThat(sent[0]).isEqualTo("/topic/events/chat-message");
        assertThat(sent[1])
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(EventEnvelope.class))
                .satisfies(envelope -> {
                    assertThat(envelope.type()).isEqualTo(EventType.CHAT_MESSAGE);
                    assertThat(envelope.eventId()).isNotNull();
                    assertThat(envelope.occurredAt()).isNotNull();
                    assertThat(envelope.payload()).isSameAs(payload);
                });
    }

    @Test
    @DisplayName("Un événement STREAM_STARTED atterrit sur /topic/events/stream-started")
    void stram_started_sur_son_topic() {
        publisher.publish(EventType.STREAM_STARTED, new Object());

        assertThat(messaging.sent).hasSize(1);
        assertThat(messaging.sent.get(0)[0]).isEqualTo("/topic/events/stream-started");
    }

    @Test
    @DisplayName("Diffusion désactivée : rien n'est envoyé sur le WebSocket")
    void diffusion_desactivee() {
        properties.setEnabled(false);

        publisher.publish(EventType.NOTIFICATION_CREATED, new Object());

        assertThat(messaging.sent).isEmpty();
    }

    /** Faux template qui enregistre (destination, payload) au lieu d'envoyer. */
    private static final class RecordingMessagingTemplate extends org.springframework.messaging.simp.SimpMessagingTemplate {

        private final List<Object[]> sent = new ArrayList<>();

        private RecordingMessagingTemplate() {
            super(new ExecutorSubscribableChannel());
        }

        @Override
        public void convertAndSend(String destination, Object payload) {
            sent.add(new Object[] {destination, payload});
        }
    }
}