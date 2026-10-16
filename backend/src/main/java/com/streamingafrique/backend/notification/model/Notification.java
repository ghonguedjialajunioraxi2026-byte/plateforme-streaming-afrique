package com.streamingafrique.backend.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "Notification" → table {@code notifications}.
 *
 * RÔLE : une interaction adressée à un utilisateur (nouvel abonné,
 * direct qui commence, nouvelle vidéo, nouveau clip, ...). Le contenu
 * est DÉNORMALISÉ volontairement (title + message + lien) : on fige le
 * message au moment de sa création, comme une notification de mobile.
 *
 * Compatibilité temps réel (sous-étape 7.17) : la ligne persistée est la
 * VÉRITÉ (le WebSocket ne fait que diffuser). Le {@code payload} JSON
 * libre voyage tel quel dans l'enveloppe WS (ids source, montants,
 * expéditeur…) : le diffuseur n'a rien à reconstruire, le client trouve
 * le même contrat en REST (relecture) et en WS (push).
 *
 * Index composé (recipient_id, is_read) : la requête du "centre de
 * notifications" (toutes mes notifications NON lues) est la plus
 * fréquente — l'index la couvre sans coût de tri.
 *
 * Relation unidirectionnelle @ManyToOne (LAZY) vers le destinataire.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id, is_read"),
        @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id, created_at"),
        @Index(name = "idx_notifications_recipient_type", columnList = "recipient_id,type")
})
public class Notification extends BaseEntity {

    /** Les types d'interactions possibles (sous-étape 7.17, additif). */
    public enum Type {
        NEW_FOLLOWER, SUBSCRIPTION, STREAM_STARTED, NEW_VIDEO, NEW_CLIP,
        REPLY, MENTION, GIFT, PAYMENT, MODERATION, SYSTEM
    }

    /** Le destinataire — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /** Coche ou non (défaut : non lue). */
    @Column(nullable = false)
    private boolean isRead;

    /** Lien vers le contenu concerné ("/@ada", "/streams/42", ...). */
    @Column(length = 500)
    private String linkUrl;

    /**
     * Données complémentaires JSON (ex. {"streamId":7,"senderId":3}).
     * Texte libre validé applicativement (pas de schéma arrêté : un schéma
     * strict par type viendra avec les émetteurs métier). NULL = aucune.
     */
    @Column(length = 2000)
    private String payload;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Notification() {
    }

    /** Fabrique : la création passe TOUJOURS par ici pour garantir la cohérence. */
    public static Notification of(User recipient, Type type, String title, String message, String linkUrl) {
        return of(recipient, type, title, message, linkUrl, null);
    }

    /** Fabrique avec données complémentaires (push WS + relecture REST). */
    public static Notification of(User recipient, Type type, String title, String message,
                                  String linkUrl, String payload) {
        Notification notification = new Notification();
        notification.recipient = recipient;
        notification.type = type;
        notification.title = title;
        notification.message = message;
        notification.linkUrl = linkUrl;
        notification.payload = payload;
        notification.isRead = false;
        return notification;
    }

    /** Marque la notification comme lue (action utilisateur). */
    public void markAsRead() {
        this.isRead = true;
    }

    public User getRecipient() {
        return recipient;
    }

    public Type getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return isRead;
    }

    public String getLinkUrl() {
        return linkUrl;
    }

    public String getPayload() {
        return payload;
    }
}