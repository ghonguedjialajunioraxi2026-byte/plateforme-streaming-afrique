package com.streamingafrique.backend.admin.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.model.AdminAuditLog;
import com.streamingafrique.backend.admin.repository.AdminAuditLogRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Journal d'audit (étape 17) — l'unique porte d'écriture vers
 * {@code admin_audit_log} (append-only : aucune mise à jour ni suppression
 * n'existe sur l'entité).
 *
 * Chaque action administrative ou de modération importante y laisse une
 * ligne (qui, quoi, sur quoi, avant/après, motif). Jamais de secret.
 */
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository audit;
    private final UserRepository users;

    public AdminAuditService(AdminAuditLogRepository audit, UserRepository users) {
        this.audit = audit;
        this.users = users;
    }

    /**
     * Trace un acte (l'acteur est résolu depuis le JWT — jamais du frontend).
     */
    @Transactional
    public void record(CurrentUser actor, String action, String targetType, Long targetId,
                       String beforeJson, String afterJson, String reason) {
        CurrentUser.requireAuthenticated(actor);
        User author = users.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Compte acteur introuvable"));
        audit.save(AdminAuditLog.record(author, action, targetType, targetId,
                beforeJson, afterJson, reason));
    }
}
