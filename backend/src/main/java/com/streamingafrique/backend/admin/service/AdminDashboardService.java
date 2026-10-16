package com.streamingafrique.backend.admin.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.dto.AdminDashboardDto;
import com.streamingafrique.backend.admin.repository.AdminAuditLogRepository;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.moderation.model.ModerationReport;
import com.streamingafrique.backend.moderation.repository.ModerationReportRepository;
import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.payment.repository.PaymentRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.subscription.model.Subscription;
import com.streamingafrique.backend.subscription.repository.SubscriptionRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.repository.VideoRepository;

/**
 * Vue d'ensemble plateforme (étape 17 : {@code GET /api/admin/dashboard}).
 *
 * Que des COUNT SQL (aucune entité chargée). Les paiements sont ventilés par
 * statut : montants multi-devises, donc AUCUN total monétaire global (jamais
 * de mélange de devises).
 */
@Service
public class AdminDashboardService {

    private final UserRepository users;
    private final CreatorProfileRepository channels;
    private final StreamRepository streams;
    private final VideoRepository videos;
    private final ClipRepository clips;
    private final FollowRepository follows;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final ModerationReportRepository reports;
    private final AdminAuditLogRepository audit;

    public AdminDashboardService(UserRepository users,
                                 CreatorProfileRepository channels,
                                 StreamRepository streams,
                                 VideoRepository videos,
                                 ClipRepository clips,
                                 FollowRepository follows,
                                 SubscriptionRepository subscriptions,
                                 PaymentRepository payments,
                                 ModerationReportRepository reports,
                                 AdminAuditLogRepository audit) {
        this.users = users;
        this.channels = channels;
        this.streams = streams;
        this.videos = videos;
        this.clips = clips;
        this.follows = follows;
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.reports = reports;
        this.audit = audit;
    }

    /** Compteurs plateforme (lecture seule, ADMIN au contrôleur). */
    @Transactional(readOnly = true)
    public AdminDashboardDto overview() {
        Map<String, Long> paymentsByStatus = new LinkedHashMap<>();
        for (Payment.Status status : Payment.Status.values()) {
            paymentsByStatus.put(status.name(), payments.countByStatus(status));
        }
        return new AdminDashboardDto(
                users.count(),
                users.countByStatus(User.Status.ACTIVE),
                users.countByStatus(User.Status.SUSPENDED),
                channels.count(),
                streams.countByStatus(Stream.Status.LIVE),
                videos.count(),
                clips.count(),
                follows.count(),
                subscriptions.countByStatus(Subscription.Status.ACTIVE),
                paymentsByStatus,
                reports.countByStatus(ModerationReport.Status.OPEN),
                audit.count());
    }
}
