package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;

/**
 * API publique de publication d'événements temps réel.
 *
 * Tout producteur (service de domaine, tâche planifiée, endpoint) publie ici :
 * le serveur construit l'événement (identifiant + horodatage), le distribue
 * aux {@link StreamEventHandler} enregistrés et, via le handler de diffusion
 * WebSocket, le pousse aux abonnés du topic de son type.
 *
 * EXTENSIBILITÉ : un producteur ne connaît jamais le détail de la distribution.
 * Il suffit d'implémenter {@link StreamEventHandler} (un bean Spring) pour se
 * brancher sur un type sans toucher au cœur.
 */
public interface StreamEventPublisher {

    /** Construit puis publie un événement du type donné avec le payload métier. */
    void publish(EventType type, Object payload);

    /** Publie un événement déjà construit (identifiant/horodatage maîtrisés). */
    void publish(StreamEvent event);
}