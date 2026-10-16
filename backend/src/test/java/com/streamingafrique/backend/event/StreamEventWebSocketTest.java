package com.streamingafrique.backend.event;

import com.streamingafrique.backend.event.dto.ChatMessagePayload;
import com.streamingafrique.backend.event.dto.EventEnvelope;
import com.streamingafrique.backend.event.dto.PublishEventRequest;
import com.streamingafrique.backend.event.model.StreamEvent;
import com.streamingafrique.backend.event.service.StreamEventDispatcher;
import com.streamingafrique.backend.event.service.StreamEventHandler;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration de l'architecture d'événements (étape 6.22) via une vraie
 * connexion WebSocket STOMP + injection par REST :
 *   - publier un événement FICTIF (REST) → diffusion reçue sur le topic du type ;
 *   - publier un événement en l'envoyant soi-même en STOMP (/app/events/publish) ;
 *   - brancher un traitant à chaud → il reçoit l'événement dans le même temps.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.stream-events.demo-interval-ms=60000",
        "app.presence.enabled=false",
        "app.websocket.demo-pulse-interval-ms=60000"
})
class StreamEventWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StreamEventDispatcher dispatcher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private WebSocketStompClient stompClient;

    /** Compte d'édition (SEC-003 : la publication REST exige un authentifié). */
    private String publisherEmail;
    private static final String PUBLISHER_PASSWORD = "ws-publish-6.22";

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper());
        stompClient.setMessageConverter(converter);
        publisherEmail = "editeur-ws-" + UUID.randomUUID() + "@test.fr";
        User publisher = User.create(publisherEmail, "Editeur WS",
                passwordEncoder.encode(PUBLISHER_PASSWORD));
        userRepository.save(publisher);
    }

    @AfterEach
    void drainDisconnected() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Événement fictif injecté par REST → diffusé sur /topic/events/chat-message")
    void evenement_fictif_injecte_rest_est_diffuse() throws Exception {
        StompSession session = connect();
        try {
            BlockingQueue<EventEnvelope> events = subscribe(session, "/topic/events/chat-message", EventEnvelope.class);

            UUID eventId = publishViaRest(EventType.CHAT_MESSAGE, Map.of(
                    "streamId", 42,
                    "authorId", 7,
                    "authorName", "Ada",
                    "text", "Bonjour la plateforme !"));

            EventEnvelope envelope = awaitOnQueue(events,
                    e -> eventId.equals(e.eventId()) && e.type() == EventType.CHAT_MESSAGE);
            assertThat(envelope).as("enveloppe de l'événement fictif").isNotNull();
            assertThat(envelope.occurredAt()).isNotNull();
            assertThat(envelope.payload()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("text", "Bonjour la plateforme !")
                    .containsEntry("streamId", 42);
        } finally {
            disconnect(session);
        }
    }

    @Test
    @DisplayName("Événement envoyé par le client en STOMP → diffusé au topic comme les autres")
    void evenement_envoye_en_stomp_est_diffuse() throws Exception {
        StompSession session = connect();
        try {
            BlockingQueue<EventEnvelope> events = subscribe(session, "/topic/events/notification-created", EventEnvelope.class);

            session.send("/app/events/publish",
                    new PublishEventRequest(EventType.NOTIFICATION_CREATED, Map.of("label", "Bienvenue")));

            EventEnvelope envelope = awaitOnQueue(events,
                    e -> e.type() == EventType.NOTIFICATION_CREATED
                            && Map.of("label", "Bienvenue").equals(e.payload()));
            assertThat(envelope).as("événement envoyé via WebSocket").isNotNull();
        } finally {
            disconnect(session);
        }
    }

    @Test
    @DisplayName("Un traitant branché à chaud reçoit l'événement en plus de la diffusion")
    void traitant_branche_a_chaud_est_averti() throws Exception {
        RecordingHandler handler = new RecordingHandler();
        dispatcher.register(handler);

        StompSession session = connect();
        try {
            BlockingQueue<EventEnvelope> events = subscribe(session, "/topic/events/follow-created", EventEnvelope.class);

            UUID eventId = publishViaRest(EventType.FOLLOW_CREATED, Map.of("followerId", 1, "followedId", 2));

            EventEnvelope envelope = awaitOnQueue(events, e -> eventId.equals(e.eventId()));
            assertThat(envelope).as("diffusion de l'événement").isNotNull();
            assertThat(handler.received())
                    .as("le traitant à chaud a reçu l'événement")
                    .extracting(StreamEvent::eventId)
                    .contains(eventId);
        } finally {
            disconnect(session);
        }
    }

    /* ─── Helpers ────────────────────────────────────────────── */

    private UUID publishViaRest(EventType type, Map<String, Object> payload) {
        // SEC-003 : injection REST authentifiée (Basic du compte d'édition).
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(publisherEmail, PUBLISHER_PASSWORD);
        HttpEntity<PublishEventRequest> entity =
                new HttpEntity<>(new PublishEventRequest(type, payload), headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.exchange("/api/events/publish", HttpMethod.POST, entity, Map.class)
                .getBody();
        assertThat(body).as("réponse de publication").isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return UUID.fromString((String) data.get("eventId"));
    }

    private StompSession connect() throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.set("accept-version", "1.2");
        headers.set("heart-beat", "0,0");
        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync(new URI("ws://localhost:" + port + "/ws"),
                null,
                headers,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders response) {
                        future.complete(session);
                    }
                });
        return future.get(10, TimeUnit.SECONDS);
    }

    private static <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> type) {
        BlockingQueue<T> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((T) payload);
            }
        });
        return received;
    }

    private static void disconnect(StompSession session) {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    private static <T> T awaitOnQueue(BlockingQueue<T> queue, Predicate<T> predicate)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            T item = queue.poll(500, TimeUnit.MILLISECONDS);
            if (item != null && predicate.test(item)) {
                return item;
            }
        }
        return null;
    }

    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /** Traitant simple branché à chaud, qui mémorise les événements reçus. */
    private static final class RecordingHandler implements StreamEventHandler {

        private final List<StreamEvent> received = Collections.synchronizedList(new ArrayList<>());

        @Override
        public java.util.Set<EventType> handles() {
            return EnumSet.of(EventType.FOLLOW_CREATED);
        }

        @Override
        public void handle(StreamEvent event) {
            received.add(event);
        }

        List<StreamEvent> received() {
            return received;
        }
    }
}