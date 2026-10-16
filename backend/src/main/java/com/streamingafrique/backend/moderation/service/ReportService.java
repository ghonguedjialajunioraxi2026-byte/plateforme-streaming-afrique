package com.streamingafrique.backend.moderation.service;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.service.AdminAuditService;
import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.repository.ClipRepository;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.moderation.dto.DecideActionRequest;
import com.streamingafrique.backend.moderation.dto.FileReportRequest;
import com.streamingafrique.backend.moderation.dto.ModerationActionDto;
import com.streamingafrique.backend.moderation.dto.ReportDto;
import com.streamingafrique.backend.moderation.dto.UserSummaryDto;
import com.streamingafrique.backend.moderation.model.ModerationAction;
import com.streamingafrique.backend.moderation.model.ModerationReport;
import com.streamingafrique.backend.moderation.repository.ModerationActionRepository;
import com.streamingafrique.backend.moderation.repository.ModerationReportRepository;
import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.service.NotificationService;
import com.streamingafrique.backend.redis.CacheKeys;
import com.streamingafrique.backend.redis.RedisCacheService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;
import com.streamingafrique.backend.video.service.VideoService;

/**
 * Signalements et sanctions (étape 17).
 *
 * Workflow : tout connecté signale (file) → MODERATOR+ analyse (file par
 * statut) → accepte/rejette → décide (sanction exécutée côté serveur +
 * notifications + audit). Les effets réutilisent les services métier
 * (visibilité VOD/clips, suspension de compte/chaîne, masquage de message).
 *
 * Périmètres : un MODERATOR agit sur les contenus ; la gestion des COMPTES
 * (statut) et des RÔLES reste ADMIN (AdminUserService, UserService).
 */
@Service
public class ReportService {

    private final ModerationReportRepository reports;
    private final ModerationActionRepository actions;
    private final UserRepository users;
    private final CreatorProfileRepository channels;
    private final StreamRepository streams;
    private final VideoRepository videos;
    private final ClipRepository clips;
    private final ChatMessageRepository messages;
    private final VideoService videoService;
    private final ClipService clipService;
    private final NotificationService notifications;
    private final RedisCacheService cache;
    private final AdminAuditService audit;

    public ReportService(ModerationReportRepository reports,
                         ModerationActionRepository actions,
                         UserRepository users,
                         CreatorProfileRepository channels,
                         StreamRepository streams,
                         VideoRepository videos,
                         ClipRepository clips,
                         ChatMessageRepository messages,
                         VideoService videoService,
                         ClipService clipService,
                         NotificationService notifications,
                         RedisCacheService cache,
                         AdminAuditService audit) {
        this.reports = reports;
        this.actions = actions;
        this.users = users;
        this.channels = channels;
        this.streams = streams;
        this.videos = videos;
        this.clips = clips;
        this.messages = messages;
        this.videoService = videoService;
        this.clipService = clipService;
        this.notifications = notifications;
        this.cache = cache;
        this.audit = audit;
    }

    /* ─── Dépôt (tout connecté) ──────────────────────────────── */

    /**
     * Signale un contenu ou un compte. Cible résolue côté serveur (type +
     * id) ; on ne signale pas son propre compte.
     */
    @Transactional
    public ReportDto file(FileReportRequest request, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        User reporter = users.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        ModerationReport report = switch (request.targetType()) {
            case USER -> {
                User target = users.findById(request.targetId())
                        .orElseThrow(() -> ApiException.notFound("Compte visé introuvable"));
                if (target.getId().equals(reporter.getId())) {
                    throw ApiException.badRequest("On ne signale pas son propre compte");
                }
                yield ModerationReport.againstUser(reporter, target, request.reason(),
                        request.description());
            }
            case CHAIN -> ModerationReport.againstChain(reporter, channelOf(request.targetId()),
                    request.reason(), request.description());
            case STREAM -> ModerationReport.againstStream(reporter, streamOf(request.targetId()),
                    request.reason(), request.description());
            case VIDEO -> ModerationReport.againstVideo(reporter, videoOf(request.targetId()),
                    request.reason(), request.description());
            case CLIP -> ModerationReport.againstClip(reporter, clipOf(request.targetId()),
                    request.reason(), request.description());
            case CHAT_MESSAGE -> ModerationReport.againstMessage(reporter,
                    messageOf(request.targetId()), request.reason(), request.description());
        };
        return ReportDto.from(reports.save(report));
    }

    /* ─── File (MODERATOR+) ──────────────────────────────────── */

    /** File paginée par statut (MODERATOR+). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ReportDto> list(ModerationReport.Status status, CurrentUser actor,
                                           Pageable pageable) {
        requireModerator(actor);
        var page = reports.findByStatusOrderByCreatedAtDesc(status, pageable);
        return ApiPageResponse.from(page,
                page.getContent().stream().map(ReportDto::from).toList());
    }

    /** Détail d'un signalement (MODERATOR+). */
    @Transactional(readOnly = true)
    public ReportDto get(Long id, CurrentUser actor) {
        requireModerator(actor);
        return ReportDto.from(reportOf(id));
    }

    /** Retient un signalement (OPEN → ACCEPTED, audité). */
    @Transactional
    public ReportDto accept(Long id, CurrentUser actor) {
        requireModerator(actor);
        ModerationReport report = reportOf(id);
        report.accept();
        ReportDto dto = ReportDto.from(reports.save(report));
        audit.record(actor, "REPORT_ACCEPTED", "REPORT", report.getId(),
                ModerationReport.Status.OPEN.name(), report.getStatus().name(), null);
        return dto;
    }

    /** Rejette un signalement (OPEN → REJECTED, audité + reporter notifié). */
    @Transactional
    public ReportDto reject(Long id, CurrentUser actor) {
        requireModerator(actor);
        ModerationReport report = reportOf(id);
        report.reject();
        ReportDto dto = ReportDto.from(reports.save(report));
        audit.record(actor, "REPORT_REJECTED", "REPORT", report.getId(),
                ModerationReport.Status.OPEN.name(), report.getStatus().name(), null);
        notifications.notify(report.getReporter(), Notification.Type.MODERATION,
                "Signalement examiné",
                "Votre signalement #" + report.getId() + " a été examiné et classé sans suite.",
                null);
        return dto;
    }

    /* ─── Décision + effets ──────────────────────────────────── */

    /**
     * Sanctionne (le signalement doit être OPEN : il est retenu d'office).
     * Effets exécutés selon (action, cible), reporter et cible notifiés,
     * acte audité. TIMEOUT/BAN_TEMP exigent une échéance (garde du modèle).
     */
    @Transactional
    public ModerationActionDto decide(Long reportId, DecideActionRequest request, CurrentUser actor) {
        requireModerator(actor);
        ModerationReport report = reportOf(reportId);
        User moderator = users.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Compte modérateur introuvable"));
        User targetUser = request.targetUserId() != null
                ? users.findById(request.targetUserId())
                        .orElseThrow(() -> ApiException.notFound("Compte visé introuvable"))
                : null;
        if (targetUser != null && targetUser.getRole() == User.Role.ADMIN) {
            throw ApiException.forbidden("Un administrateur ne se sanctionne pas ici");
        }
        final ModerationAction action;
        try {
            action = ModerationAction.decide(report, moderator, targetUser,
                    request.action(), request.scope(), request.reason(),
                    java.time.Instant.now(), request.endsAt());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        report.accept();
        reports.save(report);
        ModerationAction saved = actions.save(action);
        applyEffects(report, request, targetUser, actor);
        notifications.notify(report.getReporter(), Notification.Type.MODERATION,
                "Signalement traité",
                "Votre signalement #" + report.getId() + " a abouti à une mesure de modération.",
                null);
        audit.record(actor, "MODERATION_DECIDED", "REPORT", report.getId(),
                ModerationReport.Status.OPEN.name(),
                request.action().name() + "/" + request.scope().name(), request.reason());
        return ModerationActionDto.from(saved);
    }

    /** Historique des sanctions (MODERATOR+, filtre d'état optionnel, paginé). */
    @Transactional(readOnly = true)
    public ApiPageResponse<ModerationActionDto> actions(ModerationAction.State state,
                                                       CurrentUser actor, Pageable pageable) {
        requireModerator(actor);
        var page = state != null
                ? actions.findByStateOrderByCreatedAtDesc(state, pageable)
                : actions.findAllByOrderByCreatedAtDesc(pageable);
        return ApiPageResponse.from(page,
                page.getContent().stream().map(ModerationActionDto::from).toList());
    }

    /**
     * Lève une sanction ACTIVE (MODERATOR+, auditée). Réactive le compte ou
     * la chaîne si la sanction levée était la dernière mesure privative
     * encore active les visant (sinon, réactivation explicite côté admin).
     */
    @Transactional
    public ModerationActionDto lift(Long actionId, CurrentUser actor) {
        requireModerator(actor);
        ModerationAction action = actions.findById(actionId)
                .orElseThrow(() -> ApiException.notFound("Sanction introuvable"));
        action.lift();
        ModerationActionDto dto = ModerationActionDto.from(actions.save(action));
        audit.record(actor, "MODERATION_LIFTED", "MODERATION_ACTION", action.getId(),
                ModerationAction.State.ACTIVE.name(), action.getState().name(), null);
        if (action.getTargetUser() != null) {
            reactivateAccountIfClear(action.getTargetUser(), actor);
        }
        return dto;
    }

    /**
     * Fiche modérateur d'un compte (MODERATOR+) : profil restreint (jamais
     * d'email) + 10 dernières sanctions. L'email et la gestion du compte
     * restent ADMIN (/api/admin/users).
     */
    @Transactional(readOnly = true)
    public UserSummaryDto userSummary(Long userId, CurrentUser actor) {
        requireModerator(actor);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        List<ModerationActionDto> recent = actions
                .findByTargetUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(10)
                .map(ModerationActionDto::from)
                .toList();
        return UserSummaryDto.of(user, recent);
    }

    /* ─── Effets ─────────────────────────────────────────────── */

    private void applyEffects(ModerationReport report, DecideActionRequest request,
                              User targetUser, CurrentUser actor) {
        switch (request.action()) {
            case DISMISS -> { /* classement : acte seul */ }
            case WARN -> {
                if (targetUser != null) {
                    notifications.notify(targetUser, Notification.Type.MODERATION,
                            "Avertissement de modération", request.reason(), null);
                }
            }
            case TIMEOUT, BAN_TEMP, BAN_PERM -> {
                if (targetUser == null) {
                    throw ApiException.badRequest("Cette sanction exige un compte visé");
                }
                User.Status before = targetUser.getStatus();
                targetUser.changeStatus(User.Status.SUSPENDED);
                users.save(targetUser);
                audit.record(actor, "USER_SUSPENDED", "USER", targetUser.getId(),
                        before.name(), User.Status.SUSPENDED.name(),
                        request.action().name() + " : " + request.reason());
                notifications.notify(targetUser, Notification.Type.MODERATION,
                        "Compte suspendu", request.reason(), null);
            }
            case HIDE_CONTENT -> hideTarget(report, request.reason(), actor);
            case SUSPEND_CHAIN -> suspendChainOf(report, targetUser, request.reason(), actor);
        }
    }

    /** Masque la cible (message, VOD, clip, direct non LIVE, chaîne). */
    private void hideTarget(ModerationReport report, String reason, CurrentUser actor) {
        switch (report.getTargetType()) {
            case CHAT_MESSAGE -> {
                ChatMessage message = report.getTargetMessage();
                if (message.getStatus() != ChatMessage.Status.HIDDEN) {
                    message.hide(reason);
                }
            }
            case VIDEO -> videoService.changeVisibility(
                    report.getTargetVideo().getId(), Video.Visibility.PRIVATE, actor);
            case CLIP -> clipService.changeVisibility(
                    report.getTargetClip().getId(), Clip.Visibility.PRIVATE, actor);
            case STREAM -> {
                Stream stream = report.getTargetStream();
                if (stream.getStatus() == Stream.Status.LIVE) {
                    throw ApiException.badRequest(
                            "Direct en cours : utilisez l'arrêt admin, pas le masquage");
                }
                stream.markDeleted();
                streams.save(stream);
                cache.evictByPattern(CacheKeys.STREAM_LIST_PREFIX + "*");
            }
            case CHAIN -> suspendChain(report.getTargetChain(), reason, actor);
            case USER -> throw ApiException.badRequest(
                    "Pour un compte, utilisez une sanction de compte (suspension)");
        }
    }

    private void suspendChainOf(ModerationReport report, User targetUser, String reason,
                                CurrentUser actor) {
        CreatorProfile chain = report.getTargetChain();
        if (chain == null && targetUser != null) {
            chain = channels.findByUserId(targetUser.getId()).orElse(null);
        }
        if (chain == null) {
            throw ApiException.badRequest("Aucune chaîne à suspendre pour cette cible");
        }
        suspendChain(chain, reason, actor);
    }

    private void suspendChain(CreatorProfile chain, String reason, CurrentUser actor) {
        if (chain.getStatus() == CreatorProfile.Status.SUSPENDED) {
            return;
        }
        if (chain.getStatus() != CreatorProfile.Status.ACTIVE) {
            throw ApiException.badRequest("Seule une chaîne active peut être suspendue");
        }
        chain.changeStatus(CreatorProfile.Status.SUSPENDED);
        channels.save(chain);
        cache.evict(CacheKeys.creator(chain.getId()));
        cache.evict(CacheKeys.creatorBySlug(chain.getSlug()));
        audit.record(actor, "CHAIN_SUSPENDED", "CHAIN", chain.getId(),
                CreatorProfile.Status.ACTIVE.name(),
                CreatorProfile.Status.SUSPENDED.name(), reason);
    }

    /** Réactive le compte si plus aucune mesure privative ACTIVE ne le vise. */
    private void reactivateAccountIfClear(User target, CurrentUser actor) {
        boolean blocking = actions.findByTargetUserIdAndState(target.getId(),
                        ModerationAction.State.ACTIVE).stream()
                .anyMatch(a -> a.getAction() == ModerationAction.Action.TIMEOUT
                        || a.getAction() == ModerationAction.Action.BAN_TEMP
                        || a.getAction() == ModerationAction.Action.BAN_PERM);
        if (!blocking && target.getStatus() == User.Status.SUSPENDED) {
            target.changeStatus(User.Status.ACTIVE);
            users.save(target);
            audit.record(actor, "USER_REACTIVATED", "USER", target.getId(),
                    User.Status.SUSPENDED.name(), User.Status.ACTIVE.name(),
                    "Levée de la dernière sanction active");
            notifications.notify(target, Notification.Type.MODERATION,
                    "Compte réactivé",
                    "Votre dernière sanction a été levée : votre compte est réactivé.", null);
        }
    }

    /* ─── Résolution ─────────────────────────────────────────── */

    private ModerationReport reportOf(Long id) {
        return reports.findById(id)
                .orElseThrow(() -> ApiException.notFound("Signalement introuvable (id=" + id + ")"));
    }

    private CreatorProfile channelOf(Long id) {
        return channels.findById(id)
                .orElseThrow(() -> ApiException.notFound("Chaîne visée introuvable"));
    }

    private Stream streamOf(Long id) {
        return streams.findById(id)
                .orElseThrow(() -> ApiException.notFound("Direct visé introuvable"));
    }

    private Video videoOf(Long id) {
        return videos.findById(id)
                .orElseThrow(() -> ApiException.notFound("Vidéo visée introuvable"));
    }

    private Clip clipOf(Long id) {
        return clips.findById(id)
                .orElseThrow(() -> ApiException.notFound("Clip visé introuvable"));
    }

    private ChatMessage messageOf(Long id) {
        return messages.findById(id)
                .orElseThrow(() -> ApiException.notFound("Message visé introuvable"));
    }

    private static void requireModerator(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isModeratorOrAbove()) {
            throw ApiException.forbidden("Modération réservée");
        }
    }
}
