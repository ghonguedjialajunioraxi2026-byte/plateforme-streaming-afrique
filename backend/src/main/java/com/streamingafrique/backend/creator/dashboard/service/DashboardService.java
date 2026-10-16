package com.streamingafrique.backend.creator.dashboard.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorAnalyticsDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorCommunityDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorDashboardDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorModerationDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorRevenueDto;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.follow.model.Follow;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.moderation.model.ModerationReport;
import com.streamingafrique.backend.moderation.repository.ModerationReportRepository;
import com.streamingafrique.backend.notification.dto.NotificationDto;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.repository.NotificationRepository;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stats.model.StatSnapshot;
import com.streamingafrique.backend.stats.repository.StatSnapshotRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.stream.service.ViewerService;
import com.streamingafrique.backend.subscription.model.Subscription;
import com.streamingafrique.backend.subscription.repository.SubscriptionRepository;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.wallet.dto.WalletResponse;
import com.streamingafrique.backend.wallet.dto.WalletTransactionResponse;
import com.streamingafrique.backend.wallet.model.Wallet;
import com.streamingafrique.backend.wallet.model.WalletTransaction;
import com.streamingafrique.backend.wallet.repository.GiftSendRepository;
import com.streamingafrique.backend.wallet.repository.WalletRepository;
import com.streamingafrique.backend.wallet.repository.WalletTransactionRepository;

/**
 * Agrégats du Creator Studio (étape 16).
 *
 * RÈGLE D'OR (§17) : la chaîne est TOUJOURS résolue depuis l'utilisateur
 * authentifié ({@code findByUserId(actor.id())}) — aucun identifiant de
 * chaîne ne vient du frontend, l'accès au dashboard d'autrui est impossible
 * par construction (pas de paramètre à falsifier → pas d'IDOR possible).
 *
 * Performance (§19-21) : COUNT/SUM SQL + compteurs Redis, jamais de
 * chargement mémoire ; listes récentes bornées (5-10 lignes).
 */
@Service
public class DashboardService {

    private final CreatorProfileRepository channels;
    private final StreamRepository streams;
    private final ViewerService viewers;
    private final FollowRepository follows;
    private final SubscriptionRepository subscriptions;
    private final VideoRepository videos;
    private final ClipRepository clips;
    private final WalletRepository wallets;
    private final WalletTransactionRepository movements;
    private final GiftSendRepository gifts;
    private final NotificationRepository notifications;
    private final StatSnapshotRepository snapshots;
    private final ModerationReportRepository reports;

    public DashboardService(CreatorProfileRepository channels,
                            StreamRepository streams,
                            ViewerService viewers,
                            FollowRepository follows,
                            SubscriptionRepository subscriptions,
                            VideoRepository videos,
                            ClipRepository clips,
                            WalletRepository wallets,
                            WalletTransactionRepository movements,
                            GiftSendRepository gifts,
                            NotificationRepository notifications,
                            StatSnapshotRepository snapshots,
                            ModerationReportRepository reports) {
        this.channels = channels;
        this.streams = streams;
        this.viewers = viewers;
        this.follows = follows;
        this.subscriptions = subscriptions;
        this.videos = videos;
        this.clips = clips;
        this.wallets = wallets;
        this.movements = movements;
        this.gifts = gifts;
        this.notifications = notifications;
        this.snapshots = snapshots;
        this.reports = reports;
    }

    /* ─── Vue d'ensemble ───────────────────────────────────────── */

    /** GET /api/creator/dashboard : cartes, live courant, activité récente. */
    @Transactional(readOnly = true)
    public CreatorDashboardDto overview(CurrentUser actor) {
        CreatorProfile channel = requireChannel(actor);
        Long channelId = channel.getId();

        return new CreatorDashboardDto(
                new CreatorDashboardDto.ChannelInfo(channelId, channel.getDisplayName(),
                        channel.getSlug(), channel.getAvatarUrl(), channel.getStatus().name()),
                currentLive(channelId),
                follows.countByFollowedId(channelId),
                subscriptions.countByCreatorIdAndStatus(channelId, Subscription.Status.ACTIVE),
                streams.countByCreatorId(channelId),
                videos.countByCreatorId(channelId),
                videos.sumViewCountByCreatorId(channelId),
                clips.countByCreatorId(channelId),
                clips.sumViewCountByCreatorId(channelId),
                giftCount(channelId),
                giftShare(channelId),
                notifications.countByRecipientIdAndIsReadFalse(actor.id()),
                walletOf(actor.id()),
                recentActivity(actor.id()));
    }

    /* ─── Analytique ───────────────────────────────────────────── */

    /** GET /api/creator/analytics?days=N (1..90, défaut 30). */
    @Transactional(readOnly = true)
    public CreatorAnalyticsDto analytics(CurrentUser actor, int days) {
        if (days < 1 || days > 90) {
            throw ApiException.badRequest("La période doit être entre 1 et 90 jours");
        }
        CreatorProfile channel = requireChannel(actor);
        Long channelId = channel.getId();
        Instant to = Instant.now();
        Instant from = to.minus(days, ChronoUnit.DAYS);

        List<CreatorAnalyticsDto.SeriesPoint> series = snapshots
                .findByScopeChainIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartAsc(
                        channelId, from, to)
                .stream()
                .map(s -> new CreatorAnalyticsDto.SeriesPoint(s.getMetric().name(),
                        s.getPeriodStart(), s.getValue()))
                .toList();

        return new CreatorAnalyticsDto(days, from, to,
                videos.sumViewCountByCreatorId(channelId),
                clips.sumViewCountByCreatorId(channelId),
                follows.countByFollowedId(channelId),
                subscriptions.countByCreatorIdAndStatus(channelId, Subscription.Status.ACTIVE),
                streams.countByCreatorId(channelId),
                giftCount(channelId),
                giftShare(channelId),
                series,
                videos.findTop5ByCreatorIdOrderByViewCountDesc(channelId).stream()
                        .map(VideoDto::from).toList(),
                clips.findTop5ByCreatorIdOrderByViewCountDesc(channelId).stream()
                        .map(ClipDto::from).toList());
    }

    /* ─── Revenus ──────────────────────────────────────────────── */

    /** GET /api/creator/revenue : soldes coins + totaux par source + journal récent. */
    @Transactional(readOnly = true)
    public CreatorRevenueDto revenue(CurrentUser actor) {
        CreatorProfile channel = requireChannel(actor);
        WalletResponse wallet = walletOf(actor.id());
        Long walletId = wallets.findByOwnerId(actor.id()).map(Wallet::getId).orElse(null);

        long giftCoins = 0;
        long subscriptionCoins = 0;
        List<WalletTransactionResponse> recent = List.of();
        if (walletId != null) {
            giftCoins = movements.sumAmountByWalletIdAndKindAndStatus(walletId,
                    WalletTransaction.Kind.GIFT_RECEIPT, WalletTransaction.Status.SUCCESS);
            subscriptionCoins = movements.sumAmountByWalletIdAndKindAndStatus(walletId,
                    WalletTransaction.Kind.SUBSCRIPTION_PAYOUT, WalletTransaction.Status.SUCCESS);
            recent = movements.findByWalletIdOrderByCreatedAtDesc(walletId, PageRequest.of(0, 10))
                    .map(WalletTransactionResponse::from).getContent();
        }
        return new CreatorRevenueDto(wallet, giftCoins, subscriptionCoins,
                giftCoins + subscriptionCoins, giftCount(channel.getId()), recent);
    }

    /* ─── Communauté ───────────────────────────────────────────── */

    /** GET /api/creator/community : followers + abonnés payants récents. */
    @Transactional(readOnly = true)
    public CreatorCommunityDto community(CurrentUser actor) {
        CreatorProfile channel = requireChannel(actor);
        Long channelId = channel.getId();

        List<CreatorCommunityDto.MemberInfo> recentFollowers = follows
                .findByFollowedIdOrderByCreatedAtDesc(channelId, PageRequest.of(0, 10))
                .map(f -> new CreatorCommunityDto.MemberInfo(f.getFollower().getId(),
                        f.getFollower().getUsername(), f.getFollower().getDisplayName(),
                        f.getFollower().getAvatarUrl(), f.getCreatedAt()))
                .getContent();

        List<CreatorCommunityDto.SubscriberInfo> recentSubscribers = subscriptions
                .findByCreatorIdAndStatus(channelId, Subscription.Status.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(Subscription::getCreatedAt).reversed())
                .limit(10)
                .map(s -> new CreatorCommunityDto.SubscriberInfo(s.getSubscriber().getId(),
                        s.getSubscriber().getDisplayName(),
                        s.getPlan() != null ? s.getPlan().getName() : null,
                        s.getCreatedAt()))
                .toList();

        return new CreatorCommunityDto(follows.countByFollowedId(channelId),
                subscriptions.countByCreatorIdAndStatus(channelId, Subscription.Status.ACTIVE),
                recentFollowers, recentSubscribers);
    }

    /* ─── Modération de son espace ─────────────────────────────── */

    /** GET /api/creator/moderation : signalements ouverts visant la chaîne. */
    @Transactional(readOnly = true)
    public CreatorModerationDto moderation(CurrentUser actor) {
        CreatorProfile channel = requireChannel(actor);
        List<ModerationReport> open = reports.findByTargetChainIdAndStatusOrderByCreatedAtDesc(
                channel.getId(), ModerationReport.Status.OPEN);
        List<CreatorModerationDto.ReportInfo> recent = open.stream()
                .limit(10)
                .map(r -> new CreatorModerationDto.ReportInfo(r.getId(),
                        r.getReason() != null ? r.getReason().name() : null,
                        r.getDescription(),
                        r.getStatus() != null ? r.getStatus().name() : null,
                        r.getCreatedAt()))
                .toList();
        return new CreatorModerationDto(open.size(), recent);
    }

    /* ─── Privé ────────────────────────────────────────────────── */

    /**
     * La chaîne du connecté — 404 explicite quand le compte n'en a pas encore
     * (le frontend guide vers /creator/settings pour la créer).
     */
    private CreatorProfile requireChannel(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        return channels.findByUserId(actor.id())
                .orElseThrow(() -> ApiException.notFound(
                        "Aucune chaîne pour ce compte — créez-la depuis les paramètres"));
    }

    /** Direct LIVE courant + viewers temps réel Redis (null si OFFLINE). */
    private CreatorDashboardDto.LiveInfo currentLive(Long channelId) {
        return streams.findFirstByCreatorIdAndStatusOrderByStartedAtDesc(channelId, Stream.Status.LIVE)
                .map(s -> new CreatorDashboardDto.LiveInfo(s.getId(), s.getTitle(),
                        viewers.currentViewers(s.getId()),
                        Math.max(s.getPeakViewers(), viewers.redisPeak(s.getId())),
                        s.getStartedAt()))
                .orElse(null);
    }

    /** Portefeuille du connecté (null tant qu'aucun mouvement ne l'a créé). */
    private WalletResponse walletOf(Long userId) {
        return wallets.findByOwnerId(userId).map(WalletResponse::from).orElse(null);
    }

    /** 5 dernières notifications du connecté (activité récente du studio). */
    private List<NotificationDto> recentActivity(Long userId) {
        Page<Notification> page = notifications.findByRecipientIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 5));
        return page.map(n -> new NotificationDto(n.getId(), n.getType(), n.getTitle(),
                n.getMessage(), n.getLinkUrl(), n.isRead(), n.getCreatedAt())).getContent();
    }

    private long giftCount(Long creatorId) {
        return gifts.countByStreamCreatorId(creatorId);
    }

    private long giftShare(Long creatorId) {
        return gifts.sumShareByStreamCreatorId(creatorId);
    }
}
