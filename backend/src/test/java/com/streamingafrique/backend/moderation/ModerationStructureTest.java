package com.streamingafrique.backend.moderation;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.chat.model.ChatMessage;
import com.streamingafrique.backend.chat.repository.ChatMessageRepository;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.moderation.model.ModerationAction;
import com.streamingafrique.backend.moderation.model.ModerationReport;
import com.streamingafrique.backend.moderation.repository.ModerationActionRepository;
import com.streamingafrique.backend.moderation.repository.ModerationReportRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.19 : signalements + décisions/sanctions, historique immuable
 * (H2, transaction annulée). Aucune suppression physique testée —
 * volontairement : l'historique ne se détruit pas.
 */
@SpringBootTest
@Transactional
class ModerationStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private ModerationReportRepository reports;
    @Autowired
    private ModerationActionRepository actions;

    private User compte(String email) {
        return users.save(User.create(email, email.split("@")[0], "hash"));
    }

    @Test
    void signalement_message_puis_masquage_et_sanction_timeout() {
        User reporter = compte("temoin@example.com");
        User troll = compte("trollmodo@example.com");
        User modo = compte("modo@example.com");
        modo.changeRole(User.Role.MODERATOR);
        users.saveAndFlush(modo);
        CreatorProfile channel = channels.save(CreatorProfile.create(compte("host@example.com"), "Host TV", "host-tv"));
        Stream live = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));
        ChatMessage spam = messages.save(ChatMessage.userMessage(live, troll, "Troll", "spam", "spam"));

        ModerationReport report = reports.save(
                ModerationReport.againstMessage(reporter, spam, ModerationReport.Reason.SPAM, "Flood"));
        assertEquals(ModerationReport.Status.OPEN, report.getStatus());
        assertEquals(ModerationReport.TargetType.CHAT_MESSAGE, report.getTargetType());
        assertEquals(spam.getId(), report.getTargetMessage().getId());
        assertTrue(reports.countByStatus(ModerationReport.Status.OPEN) >= 1);

        spam.hide("Spam avéré");
        messages.saveAndFlush(spam);
        report.accept();
        reports.saveAndFlush(report);

        Instant debut = Instant.now();
        ModerationAction timeout = actions.save(ModerationAction.decide(report, modo, troll,
                ModerationAction.Action.TIMEOUT, ModerationAction.Scope.MESSAGE,
                "Flood répété", debut, debut.plusSeconds(3600)));
        assertEquals(ModerationAction.State.ACTIVE, timeout.getState());
        assertEquals(1, actions.findByTargetUserIdAndState(
                troll.getId(), ModerationAction.State.ACTIVE).size());

        timeout.markExpired();
        actions.saveAndFlush(timeout);
        assertTrue(actions.findByTargetUserIdAndState(
                troll.getId(), ModerationAction.State.ACTIVE).isEmpty());
        // L'historique demeure : 1 décision au dossier, même expirée.
        assertEquals(1, actions.findByTargetUserIdOrderByCreatedAtDesc(troll.getId()).size());
    }

    @Test
    void fabriques_incompletes_et_sanctions_mal_bornee_refusees() {
        User reporter = compte("vigie@example.com");
        assertThrows(IllegalArgumentException.class, () ->
                ModerationReport.againstUser(reporter, null, ModerationReport.Reason.HATE, "x"));
        assertThrows(IllegalArgumentException.class, () ->
                ModerationAction.decide(null, null, null, ModerationAction.Action.WARN,
                        ModerationAction.Scope.ACCOUNT, "x", Instant.now(), null));
        User modo = compte("modo2@example.com");
        User cible = compte("cible@example.com");
        Instant debut = Instant.now();
        assertThrows(IllegalArgumentException.class, () ->
                ModerationAction.decide(null, modo, cible, ModerationAction.Action.TIMEOUT,
                        ModerationAction.Scope.MESSAGE, "sans fin", debut, null));
        assertThrows(IllegalArgumentException.class, () ->
                ModerationAction.decide(null, modo, cible, ModerationAction.Action.WARN,
                        ModerationAction.Scope.ACCOUNT, "   ", debut, null));
    }

    @Test
    void action_office_sans_signalement_et_rejet_conserve() {
        User modo = compte("modo3@example.com");
        User cible = compte("cible3@example.com");
        User reporter = compte("vigie3@example.com");

        ModerationAction ban = actions.save(ModerationAction.decide(null, modo, cible,
                ModerationAction.Action.BAN_TEMP, ModerationAction.Scope.ACCOUNT,
                "Menaces", Instant.now(), Instant.now().plusSeconds(86400)));
        assertNull(ban.getReport());

        ban.lift();
        actions.saveAndFlush(ban);
        assertEquals(ModerationAction.State.LIFTED,
                actions.findById(ban.getId()).orElseThrow().getState());

        ModerationReport abusif = reports.save(
                ModerationReport.againstUser(reporter, cible, ModerationReport.Reason.OTHER, "règlement de comptes"));
        abusif.reject();
        reports.saveAndFlush(abusif);
        // Rejeté mais conservé (anti-abus de signalement).
        assertEquals(ModerationReport.Status.REJECTED,
                reports.findById(abusif.getId()).orElseThrow().getStatus());
    }
}
