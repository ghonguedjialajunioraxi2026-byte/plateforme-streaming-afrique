package com.streamingafrique.backend.admin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Étape 17 — sécurité des routes admin/modération (chemin HTTP complet,
 * Basic auth sur de vrais comptes).
 *
 * Matrice : anonyme → 401 ; USER/CREATOR → 403 sur /api/admin/** ;
 * MODERATOR → 403 sur /api/admin/** mais 200 sur /api/moderator/** ;
 * ADMIN → 200 partout. Aucune confiance accordée au frontend.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSecurityTest {

    private static final String PASSWORD = "motdepasse-17-admin";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwords;

    private String basic(String email) {
        String raw = email + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String account(String email, User.Role role) {
        User user = User.create(email, email, passwords.encode(PASSWORD));
        user.changeRole(role);
        users.save(user);
        return basic(email);
    }

    @Test
    void anonymousIsUnauthorizedOnAdminAndModerator() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/moderator/reports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/reports")
                .contentType("application/json")
                .content("{\"targetType\":\"USER\",\"targetId\":1,"
                        + "\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userAndCreatorAreForbiddenOnAdmin() throws Exception {
        String user = account("sec17user@example.com", User.Role.USER);
        String creator = account("sec17creator@example.com", User.Role.CREATOR);

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", user))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", creator))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", creator))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit").header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorForbiddenOnAdminButAllowedOnModeration() throws Exception {
        String moderator = account("sec17modo@example.com", User.Role.MODERATOR);

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", moderator))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", moderator))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/moderator/reports").header("Authorization", moderator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/moderator/dashboard").header("Authorization", moderator))
                .andExpect(status().isOk());
    }

    @Test
    void adminAllowedEverywhere() throws Exception {
        String admin = account("sec17admin@example.com", User.Role.ADMIN);

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/audit").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/config").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/moderator/reports").header("Authorization", admin))
                .andExpect(status().isOk());
    }
}
