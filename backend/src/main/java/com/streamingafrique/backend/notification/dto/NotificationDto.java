package com.streamingafrique.backend.notification.dto;

import java.time.Instant;

import com.streamingafrique.backend.notification.model.Notification;

/** DTO de SORTIE — une notification reçue par un utilisateur. */
public record NotificationDto(
        Long id,
        Notification.Type type,
        String title,
        String message,
        String linkUrl,
        boolean isRead,
        Instant createdAt
) {}