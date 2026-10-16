package com.streamingafrique.backend.admin.model;

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
 * Entité "AdminAuditLog" → table {@code admin_audit_log}.
 *
 * RÔLE : le journal des actes administratifs (sous-étape 7.20) — qui, quoi,
 * sur quoi, quand, avant/après. Append-only : AUCUN mutateur, aucune mise à
 * jour, aucune suppression (une ligne écrite ne change plus jamais ; une
 * erreur d'acte se répare par un nouvel acte, pas par un UPDATE).
 *
 * Les rôles/permissions existent déjà (User.Role ADMIN + RoleHierarchy +
 * EndpointTier + @PreAuthorize) : cette table ne duplique pas le droit,
 * elle TRACE son usage.
 */
@Entity
@Table(name = "admin_audit_log", indexes = {
        @Index(name = "idx_audit_actor_created", columnList = "actor_id,createdAt"),
        @Index(name = "idx_audit_target", columnList = "targetType,targetId")
})
public class AdminAuditLog extends BaseEntity {

    /** L'administrateur auteur de l'acte — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    /** Acte (ex. USER_ROLE_CHANGED, CHANNEL_SUSPENDED, PLAN_DEACTIVATED). */
    @Column(nullable = false, length = 100)
    private String action;

    /** Type de ressource visée (USER, CHAIN, CATEGORY, PLAN, CONFIG…). */
    @Column(nullable = false, length = 30)
    private String targetType;

    /** Identifiant de la ressource (NULL = acte global, ex. bascule config). */
    @Column
    private Long targetId;

    /** Valeur avant l'acte (JSON) — NULL si création. */
    @Column(length = 2000)
    private String beforeJson;

    /** Valeur après l'acte (JSON) — NULL si suppression logique. */
    @Column(length = 2000)
    private String afterJson;

    /** Motif rédigé (dossier). */
    @Column(length = 500)
    private String reason;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected AdminAuditLog() {
    }

    /** Fabrique : acte tracé en UNE fois, complet (qui/quand via audit). */
    public static AdminAuditLog record(User actor, String action, String targetType, Long targetId,
                                       String beforeJson, String afterJson, String reason) {
        if (actor == null || action == null || action.trim().isEmpty()
                || targetType == null || targetType.trim().isEmpty()) {
            throw new IllegalArgumentException("Acteur, action et type de cible sont obligatoires");
        }
        AdminAuditLog log = new AdminAuditLog();
        log.actor = actor;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.beforeJson = beforeJson;
        log.afterJson = afterJson;
        log.reason = reason;
        return log;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public String getReason() {
        return reason;
    }
}
