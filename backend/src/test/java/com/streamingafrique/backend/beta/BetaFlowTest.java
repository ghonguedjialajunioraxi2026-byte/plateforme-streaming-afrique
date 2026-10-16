package com.streamingafrique.backend.beta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bêta privée (étape 24) — parcours HTTP complet en mode invite-only :
 * invitations (création/liste/révocation, code montré une seule fois),
 * inscription gardée (code requis, lié à l'email, usage unique),
 * feedback (soumission/lecture/triage), matrice ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.beta.invite-only=true",
        "app.beta.invitation-ttl=1h",
})
class BetaFlowTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "beta-24-Test9";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User user(String email, User.Role role) {
        User user = User.create(email, email, passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    private static String basic(User account) {
        String raw = account.getEmail() + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String invite(User admin, String email) throws Exception {
        String body = mockMvc.perform(post("/api/admin/beta/invitations")
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "email" : "%s", "note" : "testeur" }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("code").asText();
    }

    private void register(String username, String email, String code, int expected) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(JSON).content("""
                                { "username" : "%s", "email" : "%s",
                                  "password" : "%s", "confirmPassword" : "%s",
                                  "inviteCode" : %s }
                                """.formatted(username, email, PASSWORD, PASSWORD,
                                code == null ? "null" : "\"" + code + "\"")))
                .andExpect(status().is(expected));
    }

    @Test
    @DisplayName("Invite-only : inscription sans code → 403, avec code → 201, réutilisation → 409")
    void invitation_cycle_complet() throws Exception {
        User admin = user("admin-beta-24@test.fr", User.Role.ADMIN);

        // Email distinct : l'échec 403 annule en prod (transaction), mais le
        // test partage une transaction — on évite tout résidu sur beta1.
        register("nocode", "nocode-24@test.fr", null, 403);

        String code = invite(admin, "beta1-24@test.fr");
        register("beta1", "beta1-24@test.fr", code, 201);
        // Même email + code consommé : le conflit email (409) précède —
        // aucun second compte n'est créé (usage unique effectif).
        register("beta1bis", "beta1-24@test.fr", code, 409);
    }

    @Test
    @DisplayName("Code lié à un email : autre email → 403 (pas de partage)")
    void code_lie_a_un_email() throws Exception {
        User admin = user("admin-beta2-24@test.fr", User.Role.ADMIN);

        String code = invite(admin, "invite-24@test.fr");
        register("intrus", "intrus-24@test.fr", code, 403);
        register("invite", "invite-24@test.fr", code, 201);
    }

    @Test
    @DisplayName("Révocation : le code révoqué ne fonctionne plus (403)")
    void revocation_invalide_le_code() throws Exception {
        User admin = user("admin-beta3-24@test.fr", User.Role.ADMIN);

        String created = mockMvc.perform(post("/api/admin/beta/invitations")
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "email" : "revoque-24@test.fr" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).path("data").path("id").asLong();
        String code = objectMapper.readTree(created).path("data").path("code").asText();

        mockMvc.perform(post("/api/admin/beta/invitations/{id}/revoke", id)
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        register("revoque", "revoque-24@test.fr", code, 403);
    }

    @Test
    @DisplayName("Liste admin : jamais de code en clair ; non-ADMIN → 403")
    void liste_sans_code_et_matrice_admin() throws Exception {
        User admin = user("admin-beta4-24@test.fr", User.Role.ADMIN);
        User simple = user("simple-beta4-24@test.fr", User.Role.USER);

        invite(admin, "liste-24@test.fr");

        mockMvc.perform(get("/api/admin/beta/invitations")
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].email").value("liste-24@test.fr"));

        mockMvc.perform(get("/api/admin/beta/invitations")
                        .header("Authorization", basic(simple)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/beta/invitations")
                        .header("Authorization", basic(simple))
                        .contentType(JSON).content("""
                                { "email" : "non-24@test.fr" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Feedback : anonyme 401, soumission 201, lecture soi uniquement, triage ADMIN")
    void feedback_cycle_complet() throws Exception {
        User admin = user("admin-beta5-24@test.fr", User.Role.ADMIN);
        String codeA = invite(admin, "fb-a-24@test.fr");
        String codeB = invite(admin, "fb-b-24@test.fr");
        register("fba", "fb-a-24@test.fr", codeA, 201);
        register("fbb", "fb-b-24@test.fr", codeB, 201);
        User a = userRepository.findByEmail("fb-a-24@test.fr").orElseThrow();
        User b = userRepository.findByEmail("fb-b-24@test.fr").orElseThrow();

        String feedback = """
                { "category" : "BUG", "title" : "Player figé",
                  "description" : "Le lecteur se fige après 10 s sur 4G.",
                  "route" : "/live/3", "appVersion" : "beta-0.1.0",
                  "device" : "Android 14 / Pixel" }
                """;

        mockMvc.perform(post("/api/beta/feedback")
                        .contentType(JSON).content(feedback))
                .andExpect(status().isUnauthorized());

        String created = mockMvc.perform(post("/api/beta/feedback")
                        .header("Authorization", basic(a))
                        .contentType(JSON).content(feedback))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        long feedbackId = objectMapper.readTree(created).path("data").path("id").asLong();

        // B ne voit pas les retours de A.
        mockMvc.perform(get("/api/beta/feedback/mine")
                        .header("Authorization", basic(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/beta/feedback/mine")
                        .header("Authorization", basic(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // Triage : USER → 403, ADMIN → IN_PROGRESS/P1.
        mockMvc.perform(put("/api/admin/beta/feedback/{id}/triage", feedbackId)
                        .header("Authorization", basic(a))
                        .contentType(JSON).content("""
                                { "status" : "IN_PROGRESS", "priority" : "P1" }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/beta/feedback/{id}/triage", feedbackId)
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "status" : "IN_PROGRESS", "priority" : "P1" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.priority").value("P1"));

        mockMvc.perform(get("/api/admin/beta/stats")
                        .header("Authorization", basic(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").value(1))
                .andExpect(jsonPath("$.data.accepted").value(2));
    }
}
