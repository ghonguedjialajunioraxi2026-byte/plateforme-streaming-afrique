package com.streamingafrique.backend.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.notification.model.Notification;

/**
 * Accès aux notifications {notifications}.
 *
 * Méthodes JUSTE nécessaires :
 *   - le centre de notifications (paginé, les plus récentes d'abord —
 *     index composé (recipient_id, created_at)) ;
 *   - le badge "non lues" (index (recipient_id, is_read)) ;
 *   - la marquée "tout lu" (mise à jour massive en UNE requête, sans
 *     charger les lignes en mémoire).
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Les notifications d'un utilisateur, les plus récentes d'abord. */
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    /** Badge : nombre de notifications NON lues. */
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    /** Passe toutes les notifications d'un utilisateur en "lues". */
    @Modifying
    @Query("""
            update Notification n
               set n.isRead = true
             where n.recipient.id = :recipientId
               and n.isRead = false
            """)
    int markAllAsRead(@Param("recipientId") Long recipientId);
}