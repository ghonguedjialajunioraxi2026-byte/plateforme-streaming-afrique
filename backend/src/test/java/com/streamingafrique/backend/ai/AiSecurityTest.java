package com.streamingafrique.backend.ai;

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
 * Étape 18 — sécurité des routes IA (chemin HTTP complet).
 *
 * Matrice : anonyme → 401 ; USER → 403 sur /creator/** et /moderation/** ;
 * CREATOR → 200 créateur mais 403 modération/admin ; MODERATOR → 200
 * modération mais 403 créateur-titres…​ (hiérarchie : MODERATOR implique
 * CREATOR, donc 200 — vérifié) ; ADMIN → 200 partout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiSecurityTest {

    private static final String PASSWORD = "motdepasse-18-ai";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwords;

    private String basic(String email) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (email + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    private String account(String email, User.Role role) {
        User user = User.create(email, email, passwords.encode(PASSWORD));
        user.changeRole(role);
        users.save(user);
        return basic(email);
    }

    @Test
    void anonymousIsUnauthorizedOnAi() throws Exception {
        mockMvc.perform(post("/api/ai/assistant")
                .contentType("application/json")
                .content("{\"question\":\"Bonjour\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/creator/titles")
                .contentType("application/json")
                .content("{\"topic\":\"gaming\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/ai/moderation/classify")
                .contentType("application/json")
                .content("{\"text\":\"bonjour\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ai/admin/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanUseAssistantButNotCreatorOrModeration() throws Exception {
        String user = account("ai18user@example.com", User.Role.USER);

        mockMvc.perform(post("/api/ai/assistant").header("Authorization", user)
                .contentType("application/json")
                .content("{\"question\":\"Comment suivre une chaîne ?\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/creator/titles").header("Authorization", user)
                .contentType("application/json")
                .content("{\"topic\":\"gaming\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ai/moderation/classify").header("Authorization", user)
                .contentType("application/json")
                .content("{\"text\":\"bonjour\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai/admin/usage").header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatorAllowedOnCreatorButForbiddenOnModerationAndAdmin() throws Exception {
        String creator = account("ai18creator@example.com", User.Role.CREATOR);

        mockMvc.perform(post("/api/ai/creator/titles").header("Authorization", creator)
                .contentType("application/json")
                .content("{\"topic\":\"match de foot ce soir\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/moderation/classify").header("Authorization", creator)
                .contentType("application/json")
                .content("{\"text\":\"bonjour\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ai/admin/usage").header("Authorization", creator))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorImpliesCreatorAndCanClassify() throws Exception {
        String moderator = account("ai18modo@example.com", User.Role.MODERATOR);

        mockMvc.perform(post("/api/ai/creator/titles").header("Authorization", moderator)
                .contentType("application/json")
                .content("{\"topic\":\"musique\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/moderation/classify").header("Authorization", moderator)
                .contentType("application/json")
                .content("{\"text\":\"bonjour à tous\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/admin/usage").header("Authorization", moderator))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAllowedEverywhere() throws Exception {
        String admin = account("ai18admin@example.com", User.Role.ADMIN);

        mockMvc.perform(post("/api/ai/creator/titles").header("Authorization", admin)
                .contentType("application/json")
                .content("{\"topic\":\"gaming\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/admin/usage").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/admin/features").header("Authorization", admin))
                .andExpect(status().isOk());
    }
}
