package com.streamingafrique.backend.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.admin.model.AdminAuditLog;
import com.streamingafrique.backend.admin.model.AppConfig;
import com.streamingafrique.backend.admin.repository.AdminAuditLogRepository;
import com.streamingafrique.backend.admin.repository.AppConfigRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.20 : journal d'audit immuable + configuration versionnée
 * (H2, transaction annulée). Aucun dashboard développé ici.
 */
@SpringBootTest
@Transactional
class AdminStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private AdminAuditLogRepository audit;
    @Autowired
    private AppConfigRepository configs;

    @Test
    void acte_admin_trace_qui_quoi_ressource_avant_apres() {
        User admin = users.save(User.create("admin@example.com", "admin", "hash"));
        admin.changeRole(User.Role.ADMIN);
        users.saveAndFlush(admin);
        User cible = users.save(User.create("cible@example.com", "cible", "hash"));

        AdminAuditLog acte = audit.save(AdminAuditLog.record(admin, "USER_ROLE_CHANGED", "USER",
                cible.getId(), "{\"role\":\"USER\"}", "{\"role\":\"MODERATOR\"}", "Promotion justifiée"));

        assertEquals(admin.getId(), acte.getActor().getId());
        assertEquals("USER_ROLE_CHANGED", acte.getAction());
        assertEquals("USER", acte.getTargetType());
        assertEquals(cible.getId(), acte.getTargetId());
        assertTrue(acte.getCreatedAt() != null);

        assertEquals(1, audit.findByActorIdOrderByCreatedAtDesc(admin.getId()).size());
        assertEquals(1, audit.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("USER", cible.getId()).size());
    }

    @Test
    void acte_incomplet_refuse() {
        User admin = users.save(User.create("admin2@example.com", "admin2", "hash"));
        assertThrows(IllegalArgumentException.class, () ->
                AdminAuditLog.record(null, "X", "USER", 1L, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                AdminAuditLog.record(admin, "  ", "USER", 1L, null, null, null));
    }

    @Test
    void config_versionnee_sans_ecrasement() {
        AppConfig v1 = configs.save(AppConfig.create("upload.max-image-mb", "5"));
        assertEquals(1, v1.getVersion());

        AppConfig v2 = configs.save(v1.nextVersion("10"));
        assertEquals(2, v2.getVersion());

        var historique = configs.findByConfigKeyOrderByVersionAsc("upload.max-image-mb");
        assertEquals(2, historique.size());
        assertEquals("5", historique.get(0).getValue());
        assertEquals("10", historique.get(1).getValue());
        assertTrue(configs.findByConfigKeyAndVersion("upload.max-image-mb", 1).isPresent());

        // Même clé + même version = refus (unicité).
        AppConfig doublon = AppConfig.create("upload.max-image-mb", "7");
        assertThrows(DataIntegrityViolationException.class, () -> configs.saveAndFlush(doublon));
    }
}
