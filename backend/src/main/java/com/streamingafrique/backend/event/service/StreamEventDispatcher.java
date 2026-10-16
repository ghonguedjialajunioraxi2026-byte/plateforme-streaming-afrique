package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Répartiteur d'événements : route chaque {@link StreamEvent} vers tous les
 * {@link StreamEventHandler} qui couvrent son type.
 *
 * - AUTO-DÉCOUVERTE : le constructeur recueille tous les beans Spring
 *   {@code StreamEventHandler} — ajouter un traitant (broadcast WS, cache,
 *   notification…) ne nécessite AUCUNE modification ici.
 * - ENRICHISSABLE À CHAQUE INSTANT : {@link #register} permet d'abonner un
 *   traitant à froid, même en cours d'exécution (tests, plugins).
 * - RÉSILIENT : un traitant qui échoue (Redis injoignable, JSON cassé…) est
 *   isolé par un try/catch ; les suivants ne sont pas bloqués.
 */
@Component
public class StreamEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StreamEventDispatcher.class);

    private final Map<EventType, List<StreamEventHandler>> handlersByType = new ConcurrentHashMap<>();

    public StreamEventDispatcher(List<StreamEventHandler> handlers) {
        (handlers == null ? List.<StreamEventHandler>of() : handlers).forEach(this::register);
    }

    /** Abonne un traitant à tous les types qu'il déclare (idempotent). */
    public void register(StreamEventHandler handler) {
        handler.handles().forEach(type ->
                handlersByType.computeIfAbsent(type, unused -> new CopyOnWriteArrayList<>()).add(handler));
    }

    /** Distribue l'événement aux traitants du type, chacun isolé en cas d'erreur. */
    public void dispatch(StreamEvent event) {
        List<StreamEventHandler> handlers = handlersByType.getOrDefault(event.type(), List.of());
        for (StreamEventHandler handler : handlers) {
            try {
                handler.handle(event);
            } catch (Exception e) {
                log.warn("Traitant d'événements {} en échec sur {} — ignoré, les autres continuent",
                        handler.getClass().getSimpleName(), event.type(), e);
            }
        }
    }
}