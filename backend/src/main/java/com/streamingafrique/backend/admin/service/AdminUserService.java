package com.streamingafrique.backend.admin.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.dto.AdminUserDto;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.service.NotificationService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestion administrative des comptes (étape 17).
 *
 * Recherche paginée (texte + rôle + statut), suspension / réactivation /
 * fermeture motivées (appliquées immédiatement : le login et le JWT rejettent
 * déjà les comptes non ACTIVE), notification ciblée aux utilisateurs.
 *
 * GARDE-FOUS : jamais sur soi-même, jamais sur un ADMIN (un différend entre
 * administrateurs se règle hors application — documenté, pas de guerre des
 * boutons). Le changement de RÔLE reste sur {@code PUT /api/users/{id}/role}
 * (UserService : ADMIN + anti auto-modification).
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final NotificationService notifications;
    private final AdminAuditService audit;

    public AdminUserService(UserRepository users,
                            NotificationService notifications,
                            AdminAuditService audit) {
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    /** Recherche admin (texte + filtres, paginée). */
    @Transactional(readOnly = true)
    public ApiPageResponse<AdminUserDto> search(String search, User.Role role, User.Status status,
                                               CurrentUser actor, Pageable pageable) {
        requireAdmin(actor);
        String query = search != null && !search.isBlank() ? search.trim() : null;
        var page = users.search(query, role, status, pageable);
        return ApiPageResponse.from(page,
                page.getContent().stream().map(AdminUserDto::from).toList());
    }

    /**
     * Change le statut d'un compte (SUSPENDED = bannissement temporaire ou
     * définitif selon le motif ; ACTIVE = réactivation ; CLOSED = fermeture).
     * Motif obligatoire (dossier). Acte audité + utilisateur notifié.
     */
    @Transactional
    public AdminUserDto changeStatus(Long id, User.Status status,
                                     @Size(max = 500) String reason, CurrentUser actor) {
        requireAdmin(actor);
        User target = users.findById(id)
                .orElseThrow(() -> ApiException.notFound("Compte introuvable (id=" + id + ")"));
        if (actor.id().equals(target.getId())) {
            throw ApiException.forbidden("Impossible de modifier son propre compte");
        }
        if (target.getRole() == User.Role.ADMIN) {
            throw ApiException.forbidden("Le statut d'un administrateur ne se change pas ici");
        }
        User.Status before = target.getStatus();
        target.changeStatus(status);
        users.save(target);
        audit.record(actor, "USER_STATUS_CHANGED", "USER", target.getId(),
                before.name(), status.name(), reason);
        if (status != User.Status.ACTIVE) {
            notifications.notify(target, Notification.Type.MODERATION,
                    "Mesure sur votre compte",
                    "Votre compte est passé au statut " + status.name() + " : " + reason,
                    "/profile");
        }
        return AdminUserDto.from(target);
    }

    /**
     * Notification ciblée (sanctions, informations) : destinataires explicites
     * bornés, jamais de diffusion aveugle. Acte audité (un acte par envoi).
     */
    @Transactional
    public int notifyUsers(List<Long> userIds, Notification.Type type, String title,
                           String message, String linkUrl, CurrentUser actor) {
        requireAdmin(actor);
        List<User> recipients = new ArrayList<>();
        for (Long userId : userIds) {
            users.findById(userId).ifPresent(recipients::add);
        }
        for (User recipient : recipients) {
            notifications.notify(recipient, type, title, message, linkUrl);
        }
        audit.record(actor, "USERS_NOTIFIED", "USER_GROUP", null,
                null, recipients.size() + " destinataire(s) : " + type.name(), title);
        return recipients.size();
    }

    private static void requireAdmin(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Administration réservée");
        }
    }
}
