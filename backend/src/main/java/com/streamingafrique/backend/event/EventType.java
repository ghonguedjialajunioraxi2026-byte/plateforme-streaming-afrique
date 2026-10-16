package com.streamingafrique.backend.event;

import java.util.Locale;

/**
 * Types d'événements temps réel connus de la plateforme.
 *
 * Catalogue des événements "préparés" par l'architecture (étape 6.22) : le
 * serveur les diffuse en direct aux clients abonnés et les distribue aux
 * traitements enregistrés. Ce sont des INTENTIONS de domaine — aucune
 * fonctionnalité complète de chat/stream n'est encore développée à ce stade.
 *
 * EXTENSIBILITÉ : ajouter un type d'événement = ajouter une valeur d'énum,
 * rien d'autre n'a besoin de changer dans le cœur (diffusion, distribution,
 * topic cible). Les payloads associés restent des DTO libres portés par
 * l'événement.
 */
public enum EventType {

    STREAM_STARTED,
    STREAM_ENDED,
    VIEWER_JOINED,
    VIEWER_LEFT,
    CHAT_MESSAGE,
    FOLLOW_CREATED,
    NOTIFICATION_CREATED,
    /** Étape 15 : le traitement d'une VOD a abouti / échoué. */
    VOD_READY,
    VOD_FAILED,
    /** Étape 15 : le traitement d'un clip a abouti / échoué. */
    CLIP_READY,
    CLIP_FAILED;

    /**
     * Clé lisible pour le topic de diffusion : {@code CHAT_MESSAGE → "chat-message"}.
     * Utilisée par {@link com.streamingafrique.backend.event.service.EventDestinationResolver}
     * pour construire la destination WebSocket de l'événement.
     */
    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}