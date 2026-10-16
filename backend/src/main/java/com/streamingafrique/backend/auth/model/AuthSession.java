package com.streamingafrique.backend.auth.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "AuthSession" → table {@code auth_sessions}.
 *
 * RÔLE : une session/appareil = UN refresh token RÉVOQUABLE (étape 08).
 * Le JWT d'accès reste stateless ; la persistance vit ici : rotation
 * (nouvelle ligne + révocation de l'ancienne), logout (révocation),
 * changement de mot de passe (révocation de TOUTES les sessions).
 *
 * Stockage : uniquement l'EMPREINTE SHA-256 du refresh JWT (voir
 * {@link com.streamingafrique.backend.auth.TokenHash}) — jamais le brut.
 * Les infos appareil/IP sont minimales (User-Agent tronqué, IP) : juste
 * de quoi reconnaître « cet appareil » dans /profile/settings/security.
 */
@Entity
@Table(name = "auth_sessions", indexes = {
        @Index(name = "uk_auth_sessions_token_hash", columnList = "tokenHash", unique = true),
        @Index(name = "idx_auth_sessions_user_active", columnList = "user_id,revokedAt")
})
public class AuthSession extends BaseEntity {

    /** Le compte propriétaire — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Empreinte SHA-256 du refresh JWT (64 hex) — jamais le brut. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** User-Agent tronqué (200) — NULL si inconnu. */
    @Column(length = 200)
    private String deviceInfo;

    /** Adresse IP vue à la création — NULL si inconnue. */
    @Column(length = 45)
    private String ipAddress;

    /** Dernière utilisation (refresh ou création). */
    @Column(nullable = false)
    private Instant lastUsedAt;

    /** Expiration = durée de vie du refresh JWT (7 j par défaut). */
    @Column(nullable = false)
    private Instant expiresAt;

    /** Révocation (logout, rotation, changement de mot de passe) — NULL = active. */
    private Instant revokedAt;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected AuthSession() {
    }

    /** Fabrique : session active pour un refresh fraîchement émis. */
    public static AuthSession open(User user, String tokenHash, String deviceInfo,
                                   String ipAddress, Instant expiresAt) {
        AuthSession session = new AuthSession();
        session.user = user;
        session.tokenHash = tokenHash;
        session.deviceInfo = deviceInfo;
        session.ipAddress = ipAddress;
        session.lastUsedAt = Instant.now();
        session.expiresAt = expiresAt;
        return session;
    }

    /** Session utilisable = non révoquée ET non expirée. */
    public boolean isActive() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }

    /** Activité (refresh réussi) : prolonge lastUsedAt, pas l'expiration. */
    public void touch() {
        this.lastUsedAt = Instant.now();
    }

    /** Révocation (idempotente : un second appel ne change rien). */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
