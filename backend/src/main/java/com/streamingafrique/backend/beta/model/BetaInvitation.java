package com.streamingafrique.backend.beta.model;

import java.time.Instant;

import com.streamingafrique.backend.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Invitation à la bêta privée (étape 24) — UN seul mécanisme d'accès.
 *
 * Le code en clair N'EST JAMAIS persisté : seul {@code codeHash}
 * (SHA-256 hex) est stocké — montré une fois à la création, comme les
 * stream keys. Code lié à UN email (incessible), à usage unique,
 * avec expiration. L'acceptation lie le compte créé
 * ({@code acceptedUserId}).
 */
@Entity
@Table(name = "beta_invitations")
public class BetaInvitation extends BaseEntity {

    public enum Status {
        PENDING, ACCEPTED, REVOKED
    }

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 64, unique = true)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_user_id")
    private Long acceptedUserId;

    @Column(name = "created_by_admin_id")
    private Long createdByAdminId;

    @Column(length = 280)
    private String note;

    protected BetaInvitation() {
    }

    private BetaInvitation(String email, String codeHash, Instant expiresAt,
                           Long createdByAdminId, String note) {
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdByAdminId = createdByAdminId;
        this.note = note;
    }

    public static BetaInvitation invite(String email, String codeHash, Instant expiresAt,
                                        Long createdByAdminId, String note) {
        return new BetaInvitation(email, codeHash, expiresAt, createdByAdminId, note);
    }

    public boolean isUsable() {
        return status == Status.PENDING
                && (expiresAt == null || Instant.now().isBefore(expiresAt));
    }

    public void markAccepted(Long userId) {
        this.status = Status.ACCEPTED;
        this.acceptedAt = Instant.now();
        this.acceptedUserId = userId;
    }

    public void revoke() {
        this.status = Status.REVOKED;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Long getAcceptedUserId() {
        return acceptedUserId;
    }

    public Long getCreatedByAdminId() {
        return createdByAdminId;
    }

    public String getNote() {
        return note;
    }
}
