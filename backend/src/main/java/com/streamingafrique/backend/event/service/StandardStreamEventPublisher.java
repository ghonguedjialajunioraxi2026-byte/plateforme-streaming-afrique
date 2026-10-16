package com.streamingafrique.backend.event.service;

import com.streamingafrique.backend.event.EventType;
import com.streamingafrique.backend.event.StreamEventProperties;
import com.streamingafrique.backend.event.model.StreamEvent;

import org.springframework.stereotype.Service;

/**
 * Implementation par défaut du répartiteur : c'est le SEUL point de sortie des
 * producteurs. Il assemble l'événement (identifiant, horodatage) puis le confie
 * au {@link StreamEventDispatcher}, qui le distribue aux traitants (dont la
 * diffusion WebSocket).
 *
 * Aucune logique métier ici : la liste des traitants est extensible sans
 * toucher à cette classe.
 */
@Service
public class StandardStreamEventPublisher implements StreamEventPublisher {

    private final StreamEventDispatcher dispatcher;

    public StandardStreamEventPublisher(StreamEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void publish(EventType type, Object payload) {
        publish(StreamEvent.of(type, payload));
    }

    @Override
    public void publish(StreamEvent event) {
        dispatcher.dispatch(event);
    }
}