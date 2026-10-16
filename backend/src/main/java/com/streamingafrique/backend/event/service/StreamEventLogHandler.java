package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.model.StreamEvent;

import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Exemple de traitant « métier » branché automatiquement sur l'architecture :
 * il journalise chaque événement reçu.
 *
 * C'est la PREUVE d'extensibilité : ce composant existe, écoute tous les types
 * et n'a demandé aucune modification du cœur (répartiteur, publisher, topics).
 * N'importe quel futur traitement (invalider un cache, créer une notification,
 * mettre à jour des compteurs) s'ajoutera de la même façon.
 */
@Component
public class StreamEventLogHandler implements StreamEventHandler {

    private static final Logger log = LoggerFactory.getLogger(StreamEventLogHandler.class);

    @Override
    public Set<EventType> handles() {
        return EnumSet.allOf(EventType.class);
    }

    @Override
    public void handle(StreamEvent event) {
        log.debug("Événement {} reçu (id={}) — payload {}", event.type(), event.eventId(), event.payload());
    }
}