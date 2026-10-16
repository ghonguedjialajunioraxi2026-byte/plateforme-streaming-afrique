package com.streamingafrique.backend.moderation.model;

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
 * Entité "ModerationAction" → table {@code moderation_actions}.
 *
 * RÔLE : la DÉCISION et la SANCTION (sous-étape 7.19) — véritable historique
 * des actes de modération. Chaque ligne est immuable dans son fond : on ne
 * réécrit jamais une sanction, on la lève ({@code LIFTED}) ou elle expire
 * ({@code EXPIRED}), et toute correction = nouvelle ligne.
 *
 * Sanctions : DISMISS (rejet), WARN (avertissement), TIMEOUT (exclusion
 * temporaire du chat — {@code endsAt} obligatoire), HIDE_CONTENT
 * (suppression de contenu = masquage logique), SUSPEND_CHAIN (suspension
 * de chaîne), BAN_TEMP / BAN_PERM (bannissement temporaire / définitif).
 * Peut naître d'un signalement ({@code report}) ou d'office
 * ({@code report = NULL}, patrouille modérateur).
 */
@Entity
@Table(name = "moderation_actions", indexes = {
        @Index(name = "idx_actions_target_state", columnList = "target_user_id,state"),
        @Index(name = "idx_actions_moderator", columnList = "moderator_id"),
        @Index(name = "idx_actions_report", columnList = "report_id")
})
public class ModerationAction extends BaseEntity {

    /** Sanction prononcée (immuable après création). */
    public enum Action {
        DISMISS, WARN, TIMEOUT, HIDE_CONTENT, SUSPEND_CHAIN, BAN_TEMP, BAN_PERM
    }

    /** Périmètre d'effet. */
    public enum Scope {
        MESSAGE, CONTENT, CHAIN, ACCOUNT, PLATFORM
    }

    /** État de la sanction : active → levée / expirée. */
    public enum State {
        ACTIVE, LIFTED, EXPIRED
    }

    /** Signalement d'origine — NULL = action d'office. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private ModerationReport report;

    /** Le modérateur (rôle MODERATOR ou ADMIN) — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private User moderator;

    /** Le compte sanctionné — NULL si pure suppression de contenu invité. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scope scope;

    /** Motif rédigé (dossier) — obligatoire. */
    @Column(nullable = false, length = 500)
    private String reason;

    /** Début d'effet (défaut : maintenant). */
    @Column
    private Instant startsAt;

    /** Fin d'effet — NULL = permanente (WARN sans durée, BAN_PERM, HIDE). */
    @Column
    private Instant endsAt;

    /** État par défaut : active. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state = State.ACTIVE;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected ModerationAction() {
    }

    /** Fabrique : motif obligatoire, période ordonnée si bornée. */
    public static ModerationAction decide(ModerationReport report, User moderator, User targetUser,
                                          Action action, Scope scope, String reason,
                                          Instant startsAt, Instant endsAt) {
        if (moderator == null || action == null || scope == null) {
            throw new IllegalArgumentException("Modérateur, action et périmètre sont obligatoires");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Le motif de la décision est obligatoire");
        }
        if (endsAt != null && startsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("La fin de sanction doit être après son début");
        }
        if ((action == Action.TIMEOUT || action == Action.BAN_TEMP) && endsAt == null) {
            throw new IllegalArgumentException("TIMEOUT et BAN_TEMP exigent une fin de sanction");
        }
        ModerationAction decision = new ModerationAction();
        decision.report = report;
        decision.moderator = moderator;
        decision.targetUser = targetUser;
        decision.action = action;
        decision.scope = scope;
        decision.reason = reason;
        decision.startsAt = startsAt != null ? startsAt : Instant.now();
        decision.endsAt = endsAt;
        decision.state = State.ACTIVE;
        return decision;
    }

    /** Levée anticipée (ACTIVE → LIFTED, ligne conservée). */
    public void lift() {
        if (this.state == State.ACTIVE) {
            this.state = State.LIFTED;
        }
    }

    /** Constat d'expiration (ACTIVE → EXPIRED). */
    public void markExpired() {
        if (this.state == State.ACTIVE) {
            this.state = State.EXPIRED;
        }
    }

    public ModerationReport getReport() {
        return report;
    }

    public User getModerator() {
        return moderator;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public Action getAction() {
        return action;
    }

    public Scope getScope() {
        return scope;
    }

    public String getReason() {
        return reason;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public State getState() {
        return state;
    }
}
