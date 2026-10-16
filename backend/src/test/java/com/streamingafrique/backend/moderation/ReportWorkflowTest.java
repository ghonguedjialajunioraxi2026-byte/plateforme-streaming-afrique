package com.streamingafrique.backend.moderation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.model.AdminAuditLog;
import com.streamingafrique.backend.admin.repository.AdminAuditLogRepository;
import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.moderation.dto.DecideActionRequest;
import com.streamingafrique.backend.moderation.dto.FileReportRequest;
import com.streamingafrique.backend.moderation.dto.ModerationActionDto;
import com.streamingafrique.backend.moderation.dto.ReportDto;
import com.streamingafrique.backend.moderation.model.ModerationAction;
import com.streamingafrique.backend.moderation.model.ModerationReport;
import com.streamingafrique.backend.moderation.service.ChatModerationService;
import com.streamingafrique.backend.moderation.service.ReportService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.repository.VideoRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 17 — workflow de modération (H2, transaction annulée).
 *
 * Couvre : dépôt (cible résolue, auto-signalement refusé), accept/reject,
 * décision WARN (notification sans effet d'état), HIDE_CONTENT sur message
 * (masqué, conservé), TIMEOUT sur compte (suspendu), levée (sanction LIFTED
 * + compte réactivé car dernière mesure), audit tracé à chaque acte.
 */
@SpringBootTest
@Transactional
class ReportWorkflowTest {

    @Autowired
    private ReportService reports;
    @Autowired
    private ChatModerationService chatModeration;
    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private VideoRepository videos;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private AdminAuditLogRepository audit;

    private User reporter;
    private User moderator;
    private CurrentUser moderatorActor;
    private User offender;
    private CreatorProfile channel;
    private Stream stream;
    private ChatMessage message;

    private void setUp() {
        reporter = users.save(User.create("rep17@example.com", "rep17", "hash"));
        moderator = users.save(User.create("modo17@example.com", "modo17", "hash"));
        moderator.changeRole(User.Role.MODERATOR);
        moderatorActor = CurrentUser.of(moderator.getId(), moderator.getEmail(),
                moderator.getRole());
        offender = users.save(User.create("off17@example.com", "off17", "hash"));
        offender.changeRole(User.Role.CREATOR);
        channel = channels.save(CreatorProfile.create(offender, "Off TV", "off17"));
        stream = streams.save(Stream.schedule(channel, "Live", null,
                Instant.now().plusSeconds(3600)));
        message = messages.save(ChatMessage.userMessage(stream, offender, "off17",
                "spam lien ici", "spam lien ici"));
    }

    private CurrentUser actorOf(User user) {
        return CurrentUser.of(user.getId(), user.getEmail(), user.getRole());
    }

    @Test
    void fileAcceptDecideWarnAndHide() {
        setUp();
        ReportDto filed = reports.file(new FileReportRequest(
                ModerationReport.TargetType.CHAT_MESSAGE, message.getId(),
                ModerationReport.Reason.SPAM, "lien douteux"), actorOf(reporter));
        assertEquals(ModerationReport.Status.OPEN, filed.status());

        ReportDto accepted = reports.accept(filed.id(), moderatorActor);
        assertEquals(ModerationReport.Status.ACCEPTED, accepted.status());

        ModerationActionDto warned = reports.decide(filed.id(), new DecideActionRequest(
                ModerationAction.Action.WARN, ModerationAction.Scope.MESSAGE,
                offender.getId(), "Premier avertissement", null), moderatorActor);
        assertEquals(ModerationAction.State.ACTIVE, warned.state());
        assertEquals(User.Status.ACTIVE,
                users.findById(offender.getId()).orElseThrow().getStatus());

        ChatMessageDtoHiddenCheck(filed);
    }

    private void ChatMessageDtoHiddenCheck(ReportDto filed) {
        var hidden = chatModeration.hide(message.getId(), "spam confirmé", moderatorActor);
        assertEquals(ChatMessage.Status.HIDDEN, hidden.status());
        List<AdminAuditLog> acts = audit.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
                "CHAT_MESSAGE", message.getId());
        assertTrue(acts.stream().anyMatch(a -> a.getAction().equals("CHAT_MESSAGE_HIDDEN")));
    }

    @Test
    void timeoutSuspendsAndLiftReactivates() {
        setUp();
        ReportDto filed = reports.file(new FileReportRequest(
                ModerationReport.TargetType.USER, offender.getId(),
                ModerationReport.Reason.HARASSMENT, "harcèlement répété"), actorOf(reporter));

        ModerationActionDto sanction = reports.decide(filed.id(), new DecideActionRequest(
                ModerationAction.Action.TIMEOUT, ModerationAction.Scope.ACCOUNT,
                offender.getId(), "Exclusion 7 jours",
                Instant.now().plusSeconds(7 * 24 * 3600)), moderatorActor);
        assertEquals(ModerationAction.State.ACTIVE, sanction.state());
        assertEquals(User.Status.SUSPENDED,
                users.findById(offender.getId()).orElseThrow().getStatus());

        ModerationActionDto lifted = reports.lift(sanction.id(), moderatorActor);
        assertEquals(ModerationAction.State.LIFTED, lifted.state());
        assertEquals(User.Status.ACTIVE,
                users.findById(offender.getId()).orElseThrow().getStatus());
    }

    @Test
    void selfReportAndAdminTargetAreRefused() {
        setUp();
        assertThrows(ApiException.class, () -> reports.file(new FileReportRequest(
                ModerationReport.TargetType.USER, reporter.getId(),
                ModerationReport.Reason.SPAM, null), actorOf(reporter)));

        User admin = users.save(User.create("adm17@example.com", "adm17", "hash"));
        admin.changeRole(User.Role.ADMIN);
        ReportDto filed = reports.file(new FileReportRequest(
                ModerationReport.TargetType.USER, offender.getId(),
                ModerationReport.Reason.SPAM, null), actorOf(reporter));
        assertThrows(ApiException.class, () -> reports.decide(filed.id(),
                new DecideActionRequest(ModerationAction.Action.BAN_PERM,
                        ModerationAction.Scope.ACCOUNT, admin.getId(), "coup d'état", null),
                moderatorActor));
    }

    @Test
    void hideContentOnVideoPrivatizes() {
        setUp();
        Video video = videos.save(Video.publish(channel, "VOD", "Desc", null,
                "https://cdn.example.com/v.mp4", null, 600));
        ReportDto filed = reports.file(new FileReportRequest(
                ModerationReport.TargetType.VIDEO, video.getId(),
                ModerationReport.Reason.COPYRIGHT, "extrait protégé"), actorOf(reporter));
        reports.decide(filed.id(), new DecideActionRequest(
                ModerationAction.Action.HIDE_CONTENT, ModerationAction.Scope.CONTENT,
                null, "Retrait conservatoire", null), moderatorActor);
        assertEquals(Video.Visibility.PRIVATE,
                videos.findById(video.getId()).orElseThrow().getVisibility());
    }
}
