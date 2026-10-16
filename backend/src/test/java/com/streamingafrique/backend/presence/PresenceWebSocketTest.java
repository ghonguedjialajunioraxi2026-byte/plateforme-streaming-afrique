package com.streamingafrique.backend.presence;

import com.streamingafrique.backend.presence.dto.UserPresenceDto;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.websocket.dto.DemoRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration de la présence via une vraie connexion WebSocket (étape 6.21).
 *
 * C'est le scénario utilisateur complet :
 *   - le client se connecte en STOMP en s'annonçant avec son userId → ONLINE ;
 *   - le reste du monde est prévenu par un message sur /topic/presence ;
 *   - à la déconnexion, le passage OFFLINE est diffusé puis le marqueur retiré ;
 *   - deux sessions (deux onglets) maintiennent l'utilisateur ONLINE tant qu'il
 *     reste au moins une session ;
 *   - l'API REST + Redis reflètent le même état (source de vérité = Redis).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.presence.ttl=4s",
        "app.presence.heartbeat-interval-ms=1000",
        "app.presence.sweep-interval-ms=1000",
        "app.websocket.demo-pulse-interval-ms=5000"
})
class PresenceWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestRestTemplate rest;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper());
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void drainDisconnected() {
        // Laisse les événements de déconnexion être traités avant la fin de la classe
        // (évite aux tests suivants de repartir avec des présences résiduelles).
        try {
            Thread.sleep(300);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Connexion → diffusion ONLINE ; déconnexion → diffusion OFFLINE + retrait du marqueur")
    void connexion_diffuse_online_et_deconnexion_offline() throws Exception {
        User watcher = userRepository.save(newUser("watcher"));
        User alice = userRepository.save(newUser("alice"));

        StompSession listener = connect(watcher.getId());
        try {
            BlockingQueue<UserPresenceDto> presence = subscribe(listener, "/topic/presence", UserPresenceDto.class);
            // Barrière d'ordonnancement : l'abonnement est actif quand l'écho revient.
            BlockingQueue<Object> barrier = subscribe(listener, "/topic/demo/echo", Object.class);
            listener.send("/app/echo", new DemoRequest("sync"));
            assertThat(barrier.poll(10, TimeUnit.SECONDS)).as("barrière d'ordonnancement").isNotNull();

            StompSession aliceSession = connect(alice.getId());
            try {
                UserPresenceDto online = awaitOnQueue(presence,
                        dto -> dto.userId().equals(alice.getId())
                                && "ONLINE".equals(dto.status().name()));
                assertThat(online).as("diffusion ONLINE attendue pour alice").isNotNull();
                awaitStatus(alice.getId(), "ONLINE");

                aliceSession.disconnect();

                UserPresenceDto offline = awaitOnQueue(presence,
                        dto -> dto.userId().equals(alice.getId())
                                && "OFFLINE".equals(dto.status().name()));
                assertThat(offline).as("diffusion OFFLINE attendue pour alice").isNotNull();
                awaitStatus(alice.getId(), "OFFLINE");
            } finally {
                disconnect(aliceSession);
            }
        } finally {
            disconnect(listener);
        }
    }

    @Test
    @DisplayName("Deux sessions sur deux onglets : ONLINE jusqu'à la fermeture de la dernière")
    void deux_sessions_restent_online_jusqu_a_la_derniere() throws Exception {
        User bob = userRepository.save(newUser("bob"));

        StompSession ongletA = connect(bob.getId());
        try {
            awaitStatus(bob.getId(), "ONLINE");

            StompSession ongletB = connect(bob.getId());
            try {
                // On ferme un onglet : toujours connecté sur l'autre.
                ongletA.disconnect();
                Thread.sleep(500);
                awaitStatus(bob.getId(), "ONLINE");
            } finally {
                disconnect(ongletA);
                // Dernier onglet fermé → offline.
                ongletB.disconnect();
            }
        } finally {
            disconnect(ongletA);
        }

        awaitStatus(bob.getId(), "OFFLINE");
        assertThat(statusOf(bob.getId()))
                .as("l'état REST reflète Redis : explictement OFFLINE").isEqualTo("OFFLINE");
    }

    /* ─── Helpers ────────────────────────────────────────────── */

    private User newUser(String name) {
        return User.create(name + "-" + UUID.randomUUID() + "@presence.test", name, null);
    }

    private StompSession connect(long userId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.set("userId", String.valueOf(userId));
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

    /** Attend le premier message de la file satisfaisant le prédicat (ou null au timeout). */
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

    /** Interroge l'API REST jusqu'à obtenir le statut attendu (Redis = source de vérité). */
    private void awaitStatus(Long userId, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (expected.equals(statusOf(userId))) {
                return;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("statut " + userId + " ≠ " + expected
                + " (dernier vu : " + statusOf(userId) + " à " + Instant.now() + ")");
    }

    private String statusOf(Long userId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.getForObject("/api/presence/users/{id}", Map.class, userId);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("status");
    }

    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}