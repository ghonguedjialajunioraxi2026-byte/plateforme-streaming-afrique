package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;

/**
 * Résolution de la destination WebSocket (topic) d'un type d'événement.
 *
 * Point d'extension : changer la règle de nommage des topics (par stream, par
 * domaine, une destination globale…) se fait en remplaçant ce contrat, sans
 * toucher au répartiteur ni au producteur. Implementation par défaut :
 * {@link TopicEventDestinationResolver}.
 */
@FunctionalInterface
public interface EventDestinationResolver {

    /** Topic STOMP sur lequel diffuser les événements de ce type. */
    String resolve(EventType type);
}