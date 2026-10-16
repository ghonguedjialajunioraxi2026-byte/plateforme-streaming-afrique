package com.streamingafrique.backend.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.repository.NotificationRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.17 : stockage des notifications — types étendus, payload
 * complémentaire, lu/non lu, centre par destinataire (H2, transaction annulée).
 */
@SpringBootTest
@Transactional
class NotificationStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private NotificationRepository notifications;

    @Test
    void creation_non_lue_sans_payload_par_defaut() {
        User user = users.save(User.create("notif1@example.com", "Notif1", "hash"));
        Notification notif = notifications.save(Notification.of(user, Notification.Type.NEW_FOLLOWER,
                "Nouvel abonné", "Ada vous suit", "/@ada"));

        assertFalse(notif.isRead());
        assertNull(notif.getPayload());
        assertEquals(1, notifications.countByRecipientIdAndIsReadFalse(user.getId()));
    }

    @Test
    void tous_types_stockables_avec_payload() {
        User user = users.save(User.create("notif2@example.com", "Notif2", "hash"));
        Notification.Type[] types = Notification.Type.values();
        for (Notification.Type type : types) {
            notifications.save(Notification.of(user, type, "Titre " + type,
                    "Message " + type, "/lien", "{\"type\":\"" + type + "\"}"));
        }
        notifications.flush();

        var page = notifications.findByRecipientIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 20));
        assertEquals(types.length, page.getContent().size());
        assertTrue(page.getContent().stream().allMatch(n -> n.getPayload() != null));
        assertEquals(types.length, notifications.countByRecipientIdAndIsReadFalse(user.getId()));
    }

    @Test
    void lecture_unitaire_et_massive() {
        User user = users.save(User.create("notif3@example.com", "Notif3", "hash"));
        Notification n1 = notifications.save(Notification.of(user, Notification.Type.MENTION,
                "Mention", "On vous cite", "/c/abc"));
        notifications.save(Notification.of(user, Notification.Type.GIFT,
                "Cadeau", "Kofi offre 10 coins", "/streams/1", "{\"coins\":10}"));

        n1.markAsRead();
        notifications.saveAndFlush(n1);
        assertEquals(1, notifications.countByRecipientIdAndIsReadFalse(user.getId()));

        notifications.markAllAsRead(user.getId());
        assertEquals(0, notifications.countByRecipientIdAndIsReadFalse(user.getId()));
    }
}
