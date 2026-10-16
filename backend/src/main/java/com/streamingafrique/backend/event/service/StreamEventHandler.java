package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;

import java.util.Set;

/**
 * Traitement abonné aux événements d'un type donné (SPI d'extension).
 *
 * Tout bean {@code StreamEventHandler} est découvert et enregistré
 * automatiquement auprès du {@link StreamEventDispatcher} : pour brancher une
 * réaction sur un événement (broadcast WebSocket, cache, notification,
 * journalisation…), il suffit de créer un composant Spring implémentant cette
 * interface — aucune modification du cœur publié.
 *
 * Exemples fournis par l'architecture :
 *   - {@link WebSocketStreamEventHandler} : diffuse l'événement aux clients ;
 *   - {@link StreamEventLogHandler}        : journalise les événements.
 */
public interface StreamEventHandler {

    /** Types d'événements que ce traitement sait gérer. */
    Set<EventType> handles();

    /** Réagit à l'événement (best-effort : une erreur ne bloque pas les autres). */
    void handle(StreamEvent event);
}