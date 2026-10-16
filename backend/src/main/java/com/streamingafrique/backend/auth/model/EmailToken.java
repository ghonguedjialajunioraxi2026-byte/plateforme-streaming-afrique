package com.streamingafrique.backend.auth.model;

import java.time.Instant;

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
 * Entité "EmailToken" → table {@code email_tokens}.
 *
 * RÔLE : jetons à usage unique liés à l'email (étape 08) :
 *   - VERIFY_EMAIL (24 h) : prouve la possession de l'adresse ;
 *   - PASSWORD_RESET (1 h) : autorise UN changement de mot de passe.
 *
 * Stockage : empreinte SHA-256 (jamais le brut — le brut ne voyage que dans
 * le lien, remis une seule fois). Usage unique : {@code usedAt} fige la
 * consommation (rejouer = 410/400, jamais de seconde utilisation).
 */
@Entity
@Table(name = "email_tokens", indexes = {
        @Index(name = "uk_email_tokens_hash", columnList = "tokenHash", unique = true),
        @Index(name = "idx_email_tokens_user_type", columnList = "user_id,type")
})
public class EmailToken extends BaseEntity {

    /** Usage du jeton (un type par flux, jamais mélangés). */
    public enum Type {
        VERIFY_EMAIL,
        PASSWORD_RESET
    }

    /** Le compte destinataire — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Empreinte SHA-256 du token brut (64 hex) — jamais le brut. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Usage prévu — vérifié à la consommation (un reset ne vérifie pas un email). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    /** Expiration (24 h vérification, 1 h reset). */
    @Column(nullable = false)
    private Instant expiresAt;

    /** Consommation — NULL = utilisable (sous réserve d'expiration). */
    private Instant usedAt;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected EmailToken() {
    }

    /** Fabrique : jeton frais, inutilisé. */
    public static EmailToken issue(User user, String tokenHash, Type type, Instant expiresAt) {
        EmailToken token = new EmailToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.type = type;
        token.expiresAt = expiresAt;
        return token;
    }

    /** Utilisable = non consommé ET non expiré. */
    public boolean isUsable() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    /** Consommation (idempotente : un second appel ne change rien). */
    public void markUsed() {
        if (this.usedAt == null) {
            this.usedAt = Instant.now();
        }
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Type getType() {
        return type;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
