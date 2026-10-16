package com.streamingafrique.backend.creator.dashboard.dto;

import java.time.Instant;
import java.util.List;

import com.streamingafrique.backend.notification.dto.NotificationDto;
import com.streamingafrique.backend.wallet.dto.WalletResponse;

/**
 * Vue d'ensemble du Creator Studio (étape 16 : {@code GET /api/creator/dashboard}).
 *
 * TOUT est résolu depuis l'utilisateur authentifié (JWT → compte → chaîne) :
 * aucun {@code creatorId} n'est accepté depuis le frontend (§17 — un créateur
 * ne peut jamais voir le dashboard d'un autre). Chaque chiffre vient d'une
 * agrégation SQL ou d'un compteur Redis ; PostgreSQL reste la source de
 * vérité, les valeurs temps réel (viewers) viennent de Redis.
 */
public record CreatorDashboardDto(
        ChannelInfo channel,
        LiveInfo live,
        long followers,
        long subscribers,
        long livesTotal,
        long videosTotal,
        long videoViews,
        long clipsTotal,
        long clipViews,
        long giftsReceived,
        long giftCoinsEarned,
        long unreadNotifications,
        WalletResponse wallet,
        List<NotificationDto> recentActivity
) {
    /** Chaîne du connecté (jamais d'une autre chaîne). */
    public record ChannelInfo(
            Long id,
            String displayName,
            String slug,
            String avatarUrl,
            String status
    ) {
    }

    /** Direct en cours (null quand la chaîne est OFFLINE). */
    public record LiveInfo(
            Long id,
            String title,
            long viewers,
            long peakViewers,
            Instant startedAt
    ) {
    }
}
