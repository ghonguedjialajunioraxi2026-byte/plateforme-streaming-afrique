package com.streamingafrique.backend.moderation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.video.model.Video;

/**
 * Entité "ModerationReport" → table {@code moderation_reports}.
 *
 * RÔLE : le SIGNALEMENT communautaire (sous-étape 7.19) — « ceci pose
 * problème », en attente de décision. La décision vit dans
 * {@link ModerationAction} : ce découpage évite le double workflow
 * (pas de REVIEWING/RESOLVED ici : OPEN → ACCEPTED / REJECTED).
 *
 * Cible typée XOR : exactement UNE des six cibles (utilisateur, chaîne,
 * stream, vidéo, clip, message). Vérifié en fabrique + futur CHECK DDL.
 * L'identité du reporter est conservée mais ne sera jamais exposée aux
 * sanctionnés (règle service, étape ultérieure).
 *
 * Historique : même rejeté, un signalement ne se supprime JAMAIS
 * (détection d'abus de signalement). Aucun UPDATE du fond — seul le
 * statut transite.
 */
@Entity
@Table(name = "moderation_reports", indexes = {
        @Index(name = "idx_reports_status_created", columnList = "status,createdAt"),
        @Index(name = "idx_reports_reporter", columnList = "reporter_id")
})
public class ModerationReport extends BaseEntity {

    /** Nature de la cible (exactement une renseignée). */
    public enum TargetType {
        USER, CHAIN, STREAM, VIDEO, CLIP, CHAT_MESSAGE
    }

    /** Motif (liste fermée 7.8). */
    public enum Reason {
        SPAM, HARASSMENT, HATE, SEXUAL, VIOLENCE, COPYRIGHT, OTHER
    }

    /** État du signalement : ouvert → accepté / rejeté (terminaux). */
    public enum Status {
        OPEN, ACCEPTED, REJECTED
    }

    /** Le reporter — jamais null (protégé, jamais exposé). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TargetType targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_chain_id")
    private CreatorProfile targetChain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_stream_id")
    private Stream targetStream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_video_id")
    private Video targetVideo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_clip_id")
    private Clip targetClip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_message_id")
    private ChatMessage targetMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Reason reason;

    @Column(length = 2000)
    private String description;

    /** État par défaut : ouvert. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected ModerationReport() {
    }

    private static ModerationReport base(User reporter, TargetType targetType, Reason reason, String description) {
        if (reporter == null || targetType == null || reason == null) {
            throw new IllegalArgumentException("Reporter, cible et motif sont obligatoires");
        }
        ModerationReport report = new ModerationReport();
        report.reporter = reporter;
        report.targetType = targetType;
        report.reason = reason;
        report.description = description;
        report.status = Status.OPEN;
        return report;
    }

    /** Fabriques par cible (exactement une — le type porté le garantit). */
    public static ModerationReport againstUser(User reporter, User target, Reason reason, String description) {
        ModerationReport report = base(reporter, TargetType.USER, reason, description);
        if (target == null) {
            throw new IllegalArgumentException("La cible utilisateur est requise");
        }
        report.targetUser = target;
        return report;
    }

    public static ModerationReport againstChain(User reporter, CreatorProfile target, Reason reason, String description) {
        ModerationReport report = base(reporter, TargetType.CHAIN, reason, description);
        if (target == null) {
            throw new IllegalArgumentException("La chaîne cible est requise");
        }
        report.targetChain = target;
        return report;
    }

    public static ModerationReport againstStream(User reporter, Stream target, Reason reason, String description) {
        ModerationReport report = base(reporter, TargetType.STREAM, reason, description);
        if (target == null) {
            throw new IllegalArgumentException("Le stream cible est requis");
        }
        report.targetStream = target;
        return report;
    }

    public static ModerationReport againstVideo(User reporter, Video target, Reason reason, String description) {
        ModerationReport report = base(reporter, TargetType.VIDEO, reason, description);
        if (target == null) {
            throw new IllegalArgumentException("La vidéo cible est requise");
        }
        report.targetVideo = target;
        return report;
    }

    public static ModerationReport againstClip(User reporter, Clip target, Reason reason, String description) {
        ModerationReport report = base(reporter, TargetType.CLIP, reason, description);
        if (target == null) {
            throw new IllegalArgumentException("Le clip cible est requis");
        }
        report.targetClip = target;
        return report;
    }

    public static ModerationReport againstMessage(User reporter, ChatMessage target, Reason reason, String description) {
        ModerationReport report = base(reporter, TargetType.CHAT_MESSAGE, reason, description);
        if (target == null) {
            throw new IllegalArgumentException("Le message cible est requis");
        }
        report.targetMessage = target;
        return report;
    }

    /** Signalement fondé (OPEN → ACCEPTED). */
    public void accept() {
        if (this.status == Status.OPEN) {
            this.status = Status.ACCEPTED;
        }
    }

    /** Signalement rejeté (OPEN → REJECTED, conservé anti-abus). */
    public void reject() {
        if (this.status == Status.OPEN) {
            this.status = Status.REJECTED;
        }
    }

    public User getReporter() {
        return reporter;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public User getTargetUser() {
        return targetUser;
    }

    public CreatorProfile getTargetChain() {
        return targetChain;
    }

    public Stream getTargetStream() {
        return targetStream;
    }

    public Video getTargetVideo() {
        return targetVideo;
    }

    public Clip getTargetClip() {
        return targetClip;
    }

    public ChatMessage getTargetMessage() {
        return targetMessage;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }
}
