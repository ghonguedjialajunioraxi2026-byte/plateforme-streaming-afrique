package com.streamingafrique.backend.chat.model;

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
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "ChatMessage" → table {@code chat_messages}.
 *
 * RÔLE : la PERSISTANCE du chat (sous-étape 7.18). Le temps réel (WebSocket)
 * ne fait que diffuser ; cette table est la vérité et l'historique : chaque
 * message diffusé y est inséré AVANT broadcast, jamais l'inverse.
 *
 * Salon : implicite = le stream ({@code stream_id}). Un stream = un salon,
 * pas de table rooms (aucun salon hors live à ce stade). Les messages
 * système (ouverture du live, action de modération visible) portent
 * {@code system = true} et {@code author = NULL}.
 *
 * Immuabilité : le corps ne s'édite JAMAIS (pas de UPDATE du texte).
 * Modération = {@code HIDDEN} + motif, ligne conservée pour recours.
 * {@code createdAt} (BaseEntity) EST l'horodatage d'envoi — pas de seconde
 * colonne sentAt dupliquée.
 *
 * Volume (cf. analyse en Javadoc de paquetage à venir) : ligne étroite,
 * un seul index chaud (stream_id, id), pagination keyset
 * ({@code WHERE stream_id = ? AND id > ?}), partitionnement mensuel futur.
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_stream_id", columnList = "stream_id,id")
})
public class ChatMessage extends BaseEntity {

    /** État d'affichage (jamais de DELETE physique). */
    public enum Status {
        VISIBLE, HIDDEN
    }

    /** Le salon = le direct — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id", nullable = false)
    private Stream stream;

    /**
     * L'auteur — NULL uniquement pour les messages système
     * ({@code system = true}). Règle vérifiée par le service + futur CHECK DDL.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    /** Pseudo figé au moment de l'envoi (survit aux renommages). */
    @Column(nullable = false, length = 100)
    private String authorNameSnapshot;

    /** Texte brut reçu (référence en cas de litige). */
    @Column(nullable = false, length = 2000)
    private String bodyRaw;

    /** Texte affiché (après assainissement à l'écriture). */
    @Column(nullable = false, length = 2000)
    private String bodyDisplay;

    /** Message système (annonce) vs message d'utilisateur. */
    @Column(nullable = false)
    private boolean system = false;

    /** État par défaut : visible. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.VISIBLE;

    /** Motif du masquage (modération) — NULL si visible. */
    @Column(length = 200)
    private String hiddenReason;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected ChatMessage() {
    }

    /** Fabrique : message d'utilisateur (auteur obligatoire, non vide après trim). */
    public static ChatMessage userMessage(Stream stream, User author, String authorName,
                                          String bodyRaw, String bodyDisplay) {
        if (author == null) {
            throw new IllegalArgumentException("Un message utilisateur exige un auteur");
        }
        if (bodyRaw == null || bodyRaw.trim().isEmpty()) {
            throw new IllegalArgumentException("Le corps du message ne peut pas être vide");
        }
        ChatMessage message = new ChatMessage();
        message.stream = stream;
        message.author = author;
        message.authorNameSnapshot = authorName;
        message.bodyRaw = bodyRaw;
        message.bodyDisplay = bodyDisplay;
        message.system = false;
        message.status = Status.VISIBLE;
        return message;
    }

    /** Fabrique : message système du salon (sans auteur). */
    public static ChatMessage systemMessage(Stream stream, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Le texte système ne peut pas être vide");
        }
        ChatMessage message = new ChatMessage();
        message.stream = stream;
        message.author = null;
        message.authorNameSnapshot = "Système";
        message.bodyRaw = text;
        message.bodyDisplay = text;
        message.system = true;
        message.status = Status.VISIBLE;
        return message;
    }

    /** Masquage par la modération (motif obligatoire, ligne conservée). */
    public void hide(String hiddenReason) {
        if (hiddenReason == null || hiddenReason.trim().isEmpty()) {
            throw new IllegalArgumentException("Le motif du masquage est obligatoire");
        }
        this.status = Status.HIDDEN;
        this.hiddenReason = hiddenReason;
    }

    public Stream getStream() {
        return stream;
    }

    public User getAuthor() {
        return author;
    }

    public String getAuthorNameSnapshot() {
        return authorNameSnapshot;
    }

    public String getBodyRaw() {
        return bodyRaw;
    }

    public String getBodyDisplay() {
        return bodyDisplay;
    }

    public boolean isSystem() {
        return system;
    }

    public Status getStatus() {
        return status;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }
}
