package com.streamingafrique.backend.profile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profil utilisateur (étape 09) — chemin HTTP complet.
 *
 * Couvre §38 : mon profil (lazy-create), profil public (liste blanche),
 * modification partielle + validation, avatar sécurisé, 401 anonyme.
 * Stockage isolé dans target/test-profile-uploads (nettoyé après chaque test).
 */
@SpringBootTest(properties = "app.storage.location=target/test-profile-uploads")
@AutoConfigureMockMvc
@Transactional
class ProfileFlowTest {

    private static final Path STORAGE = Path.of("target/test-profile-uploads").toAbsolutePath().normalize();
    private static final String PASSWORD = "motdepasse-09";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String basicAuth(String email) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((email + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    private User account(String email, String username) {
        User user = User.create(email, username, "Affichage", passwordEncoder.encode(PASSWORD));
        return userRepository.save(user);
    }

    private static byte[] bytes(byte[] signature) {
        byte[] bytes = new byte[256];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    @AfterEach
    void cleanStorage() throws Exception {
        if (!Files.exists(STORAGE)) {
            return;
        }
        try (var paths = Files.walk(STORAGE)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    // -------------------------------------------------- lecture

    @Test
    void mon_profil_complet_authentifie_lazy_create() throws Exception {
        User user = account("moi@test.fr", "moi");
        mockMvc.perform(get("/api/profiles/me").header("Authorization", basicAuth(user.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("moi"))
                .andExpect(jsonPath("$.data.email").value("moi@test.fr"))
                .andExpect(jsonPath("$.data.notifyLive").value(true))
                .andExpect(jsonPath("$.data.isPublic").value(true));
    }

    @Test
    void mon_profil_anonyme_401() throws Exception {
        mockMvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/profiles/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void profil_public_liste_blanche_sans_secret() throws Exception {
        User user = account("pub@test.fr", "pub");
        // Le profil naît à la première lecture du propriétaire (lazy-create).
        mockMvc.perform(get("/api/profiles/me").header("Authorization", basicAuth(user.getEmail())))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(get("/api/profiles/username/pub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("pub"))
                .andExpect(jsonPath("$.data.displayName").value("Affichage"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("moi@test.fr") || body.contains("pub@test.fr"));
        assertFalse(body.contains("passwordHash"));
        assertFalse(body.contains("birthDate"));
        assertFalse(body.contains("notifyLive"));
    }

    @Test
    void profil_public_inconnu_404_et_prive_404() throws Exception {
        mockMvc.perform(get("/api/profiles/username/fantome")).andExpect(status().isNotFound());
        User user = account("prive@test.fr", "prive");
        mockMvc.perform(put("/api/profiles/me").header("Authorization", basicAuth(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPublic\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/profiles/username/prive")).andExpect(status().isNotFound());
    }

    // -------------------------------------------------- modification

    @Test
    void modification_partielle_normalisee_et_preservee() throws Exception {
        User user = account("edit@test.fr", "edit");
        String auth = basicAuth(user.getEmail());
        mockMvc.perform(put("/api/profiles/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ada Lovelace","bio":"Dev","countryCode":"sn",\
                                "language":"fr","websiteUrl":"https://example.com",\
                                "xHandle":"@ada","birthDate":"1990-05-17"}\
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.data.countryCode").value("SN"))
                .andExpect(jsonPath("$.data.birthDate").value("1990-05-17"));
        // PUT partiel : bio absente = préservée.
        mockMvc.perform(put("/api/profiles/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"wo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bio").value("Dev"))
                .andExpect(jsonPath("$.data.language").value("wo"));
    }

    @Test
    void modification_invalide_400() throws Exception {
        User user = account("bad@test.fr", "bad");
        String auth = basicAuth(user.getEmail());
        // Nom vide.
        mockMvc.perform(put("/api/profiles/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"  \"}"))
                .andExpect(status().isBadRequest());
        // URL javascript: refusée.
        mockMvc.perform(put("/api/profiles/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"websiteUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        // Pays incohérent.
        mockMvc.perform(put("/api/profiles/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countryCode\":\"XXYZ\"}"))
                .andExpect(status().isBadRequest());
        // Naissance future.
        mockMvc.perform(put("/api/profiles/me").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthDate\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mass_assignment_role_status_ignore() throws Exception {
        User user = account("mass@test.fr", "mass");
        mockMvc.perform(put("/api/profiles/me").header("Authorization", basicAuth(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"status\":\"ACTIVE\",\"displayName\":\"Mass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Mass"));
        User reloaded = userRepository.findByEmail("mass@test.fr").orElseThrow();
        assertTrue(reloaded.getRole() == User.Role.USER);
        assertTrue(reloaded.getStatus() == User.Status.ACTIVE);
    }

    // -------------------------------------------------- avatar

    @Test
    void avatar_valide_lie_et_remplace_sans_orphelin() throws Exception {
        User user = account("avatar@test.fr", "avatar");
        String auth = basicAuth(user.getEmail());
        MvcResult first = mockMvc.perform(multipart("/api/profiles/me/avatar").file(
                                new MockMultipartFile("file", "moi.png", "image/png", bytes(PNG)))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").exists())
                .andReturn();
        String url1 = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();
        assertTrue(url1.startsWith("/uploads/avatars/"));
        Path file1 = STORAGE.resolve(url1.substring("/uploads/".length()));
        assertTrue(Files.exists(file1));

        MvcResult second = mockMvc.perform(multipart("/api/profiles/me/avatar").file(
                                new MockMultipartFile("file", "moi2.png", "image/png", bytes(PNG)))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        String url2 = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();
        assertTrue(!url1.equals(url2));
        // Ancien fichier supprimé (pas d'orphelin), nouveau présent.
        assertFalse(Files.exists(file1));
        assertTrue(Files.exists(STORAGE.resolve(url2.substring("/uploads/".length()))));
    }

    @Test
    void avatar_suspect_415_et_anonyme_401() throws Exception {
        User user = account("avbad@test.fr", "avbad");
        mockMvc.perform(multipart("/api/profiles/me/avatar").file(
                                new MockMultipartFile("file", "evil.png", "image/png",
                                        "pas une image du tout".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", basicAuth(user.getEmail())))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart("/api/profiles/me/avatar").file(
                                new MockMultipartFile("file", "a.png", "image/png", bytes(PNG))))
                .andExpect(status().isUnauthorized());
    }
}
