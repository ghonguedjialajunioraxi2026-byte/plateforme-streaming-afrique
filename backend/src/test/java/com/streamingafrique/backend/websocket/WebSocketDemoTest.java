package com.streamingafrique.backend.websocket;

import com.streamingafrique.backend.presence.service.PresenceService;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.websocket.dto.DemoMessage;
import com.streamingafrique.backend.websocket.dto.DemoRequest;
import com.streamingafrique.backend.websocket.dto.PulseMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Test d'intégration du temps réel : véritable connexion STOMP à un serveur
 * embarqué, de bout en bout, avec un vrai client WebSocket.
 *
 *  - écho : aller-retour client → serveur → client (/app/echo → /topic/demo/echo) ;
 *  - pulse : diffusion serveur → client (/topic/pulse) sans demande du client.
 *
 * La présence utilisateur (bien qu'un peu plus lourde à tester ici) a sa propre
 * classe {@code PresenceWebSocketTest}. On vérifie ici que le pulse fonctionne
 * et utilise le vrai compteur de présence en ligne.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.websocket.demo-pulse-interval-ms=1000",
        "app.presence.ttl=3s",
        "app.presence.heartbeat-interval-ms=1000",
        "app.presence.sweep-interval-ms=1000"
})
class WebSocketDemoTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PresenceService presenceService;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper());
        stompClient.setMessageConverter(converter);
    }

    @Test
    @DisplayName("Une connexion WebSocket réussit et un écho fait l'aller-retour")
    void connexion_aller_retour_echo() throws Exception {
        StompSession session = connect();
        try {
            BlockingQueue<DemoMessage> echo = subscribe(session, "/topic/demo/echo", DemoMessage.class);

            session.send("/app/echo", new DemoRequest("bonjour"));

            DemoMessage reply = echo.poll(10, TimeUnit.SECONDS);
            assertThat(reply).as("l'écho doit être reçu dans les 10 s").isNotNull();
            assertThat(reply.text()).isEqualTo("bonjour");
            assertThat(reply.sender()).isNotBlank();
            assertThat(reply.serverTime()).isNotNull();
        } finally {
            disconnect(session);
        }
    }

    @Test
    @DisplayName("Le serveur diffuse un pulse aux utilisateurs en ligne")
    void abonnement_pulse_recu() throws Exception {
        User user = userRepository.save(
                User.create("pulse-" + UUID.randomUUID() + "@test.com", "Pulse", null));
        StompSession session = connect(user.getId());
        try {
            BlockingQueue<PulseMessage> pulses = subscribe(session, "/topic/pulse", PulseMessage.class);

            PulseMessage pulse = pulses.poll(10, TimeUnit.SECONDS);
            assertThat(pulse).as("un battement doit arriver dans les 10 s").isNotNull();
            assertThat(pulse.sequence()).isPositive();
            assertThat(pulse.connectedClients()).isGreaterThanOrEqualTo(1);
            assertThat(pulse.serverTime()).isNotNull();
        } finally {
            disconnect(session);
        }
    }

    /* ─── Helpers ────────────────────────────────────────────── */

    /** Session anonyme (pas de présence liée). */
    private StompSession connect() throws Exception {
        return connect((StompHeaders) null);
    }

    /** Session liée à un utilisateur (la présence sera marquée ONLINE). */
    private StompSession connect(long userId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.set("userId", String.valueOf(userId));
        headers.set("accept-version", "1.2");
        headers.set("heart-beat", "0,0");
        return connect(headers);
    }

    private StompSession connect(StompHeaders connectHeaders) throws Exception {
        CompletableFuture<StompSession> future = new CompletableFuture<>();
        stompClient.connectAsync(new URI("ws://localhost:" + port + "/ws"),
                null,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders headers) {
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

    private static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}