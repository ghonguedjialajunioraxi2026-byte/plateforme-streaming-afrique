package com.streamingafrique.backend.config;

import com.streamingafrique.backend.presence.PresenceWebSocketLifecycle;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration du temps réel : STOMP au-dessus de WebSocket.
 *
 * Ce socle servira à toutes les fonctionnalités temps réel à venir
 * (chat live, présence, notifications, événements de stream, compteurs).
 *
 *  - Le client se connecte au point d'accès {@code /ws} (poignée de main WebSocket),
 *    avec des origines restreintes (même liste que le CORS REST).
 *  - Le broker intégré de Spring diffuse les messages aux abonnés d'un topic
 *    (préfixe {@code /topic}) — un "chatroom" ouvert, parfait pour du pub/sub.
 *  - Les messages envoyés par les clients arrivent sur {@code /app/...} :
 *    Spring les route vers les méthodes {@code @MessageMapping} correspondantes.
 *
 * Pour la démonstration, ce config active aussi {@code @EnableScheduling} :
 * le service WebSocketPulseService émet un battement périodique sur /topic/pulse.
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final PresenceWebSocketLifecycle presenceLifecycle;

    public WebSocketConfig(WebSocketProperties properties,
                           PresenceWebSocketLifecycle presenceLifecycle) {
        this.properties = properties;
        this.presenceLifecycle = presenceLifecycle;
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(presenceLifecycle);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Diffusion vers les abonnés : tout ce qui est publié sur /topic/** est
        // relayé à tous les clients qui y sont abonnés (chat, présence...).
        registry.enableSimpleBroker("/topic");

        // Messages des clients : /app/** est routé vers @MessageMapping("/...").
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getPath())
                .setAllowedOrigins(properties.getAllowedOriginsArray());
    }
}