package com.streamingafrique.backend.notification.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.notification.dto.NotificationDto;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.repository.NotificationRepository;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Notifications.
 *
 * Règles métier :
 *   - les autres services (ex. FollowService) appellent {@code notify(...)}
 *     pour CRÉER une notification : point d'entrée UNIQUE ;
 *   - la lecture est invisible pour l'utilisateur tant que isRead reste false
 *     (marquée lue à la lecture / en bloc via "tout lu").
 *
 * Autorisation (étape 6.25, durcie étape 22 / SEC-001) : les LECTURES
 * (liste, badge) portent l'ACTEUR authentifié et exigent qu'il soit le
 * DESTINATAIRE ou un ADMIN (requireOwnerOrAdmin) — les notifications sont
 * des données PRIVÉES, jamais lisibles par un tiers ni en anonyme. Les
 * marquages « lu » exigent le DESTINATAIRE (requireSelf).
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /** Crée une notification pour un utilisateur (appelé par les métiers). */
    @Transactional
    public void notify(User recipient, Notification.Type type, String title, String message, String linkUrl) {
        repository.save(Notification.of(recipient, type, title, message, linkUrl));
    }

    /** Centre de notifications d'un utilisateur : destinataire ou ADMIN uniquement (SEC-001). */
    @Transactional(readOnly = true)
    public ApiPageResponse<NotificationDto> forUser(Long recipientId, Pageable pageable, CurrentUser actor) {
        CurrentUser.requireOwnerOrAdmin(actor, recipientId);
        ensureUser(recipientId);
        Page<Notification> page = repository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        return ApiPageResponse.of(
                page.getContent().stream().map(this::toDto).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    /** Badge "non lues" : destinataire ou ADMIN uniquement (SEC-001). */
    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId, CurrentUser actor) {
        CurrentUser.requireOwnerOrAdmin(actor, recipientId);
        ensureUser(recipientId);
        return repository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    /** Marque UNE notification comme lue (seul le destinataire peut le faire). */
    @Transactional
    public NotificationDto markAsRead(Long notificationId, CurrentUser actor) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> ApiException.notFound("Notification introuvable (id=" + notificationId + ")"));
        CurrentUser.requireSelf(actor, notification.getRecipient().getId());
        notification.markAsRead();
        return toDto(repository.save(notification));
    }

    /** Marque TOUTES les notifications d'un utilisateur comme lues (1 requête SQL). */
    @Transactional
    public int markAllAsRead(Long recipientId, CurrentUser actor) {
        CurrentUser.requireSelf(actor, recipientId);
        ensureUser(recipientId);
        return repository.markAllAsRead(recipientId);
    }

    private void ensureUser(Long recipientId) {
        if (!userRepository.existsById(recipientId)) {
            throw ApiException.notFound("Compte introuvable (id=" + recipientId + ")");
        }
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getMessage(), notification.getLinkUrl(),
                notification.isRead(), notification.getCreatedAt());
    }
}