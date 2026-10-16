package com.streamingafrique.backend.creator;

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
import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.category.repository.CategoryRepository;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.follow.repository.FollowRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaînes (étape 09) — chemin HTTP complet.
 *
 * Couvre §38 : création (slug auto, catégorie, promotion), lecture publique
 * enrichie (followers, catégorie, statut), modification owner, IDOR (§40),
 * mass assignment (§40), avatar/bannière sécurisés, fermeture logique
 * (contenus intacts), 401 anonyme.
 */
@SpringBootTest(properties = "app.storage.location=target/test-channel-uploads")
@AutoConfigureMockMvc
@Transactional
class ChannelFlowTest {

    private static final Path STORAGE = Path.of("target/test-channel-uploads").toAbsolutePath().normalize();
    private static final String PASSWORD = "motdepasse-09";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreatorProfileRepository creatorRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private StreamRepository streamRepository;
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

    private Long createChannel(String email, String username, String displayName, Long categoryId)
            throws Exception {
        String body = categoryId == null
                ? "{\"displayName\":\"" + displayName + "\"}"
                : "{\"displayName\":\"" + displayName + "\",\"primaryCategoryId\":" + categoryId + "}";
        MvcResult result = mockMvc.perform(post("/api/creators").param("userId",
                                userRepository.findByEmail(email).orElseThrow().getId().toString())
                        .header("Authorization", basicAuth(email))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
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

    // -------------------------------------------------- création

    @Test
    void creation_slug_auto_promotion_categorie() throws Exception {
        User user = account("crea@test.fr", "crea");
        Category jeux = categoryRepository.save(Category.create("Jeux Vidéo", "jeux-video", "Gaming"));
        Long id = createChannel("crea@test.fr", "crea", "Créa TV", jeux.getId());

        mockMvc.perform(get("/api/creators/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("crea-tv"))
                .andExpect(jsonPath("$.data.followerCount").value(0))
                .andExpect(jsonPath("$.data.primaryCategory.slug").value("jeux-video"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertEquals(User.Role.CREATOR,
                userRepository.findByEmail("crea@test.fr").orElseThrow().getRole());
        assertTrue(user.getId() != null);
    }

    @Test
    void creation_doublon_categorie_inconnue_anonyme() throws Exception {
        account("bis@test.fr", "bis");
        account("ter@test.fr", "ter");
        createChannel("bis@test.fr", "bis", "Bis TV", null);
        // Deuxième chaîne du même compte → 409.
        mockMvc.perform(post("/api/creators").param("userId",
                                userRepository.findByEmail("bis@test.fr").orElseThrow().getId().toString())
                        .header("Authorization", basicAuth("bis@test.fr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Bis 2\"}"))
                .andExpect(status().isConflict());
        // Catégorie inexistante → 404 (jamais créée à la volée, compte frais).
        mockMvc.perform(post("/api/creators").param("userId",
                                userRepository.findByEmail("ter@test.fr").orElseThrow().getId().toString())
                        .header("Authorization", basicAuth("ter@test.fr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Autre\",\"primaryCategoryId\":999999}"))
                .andExpect(status().isNotFound());
        // Anonyme → 401.
        mockMvc.perform(post("/api/creators").param("userId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- ma chaîne (JWT)

    @Test
    void ma_chaine_via_jwt_200_404_anonyme_401() throws Exception {
        account("moi@test.fr", "moi");
        account("sans@test.fr", "sans");
        createChannel("moi@test.fr", "moi", "Moi TV", null);
        // Propriétaire dérivé du JWT : 200.
        mockMvc.perform(get("/api/creators/me").header("Authorization", basicAuth("moi@test.fr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("moi-tv"));
        // Compte sans chaîne : 404 (pas de fuite d'existence d'autrui).
        mockMvc.perform(get("/api/creators/me").header("Authorization", basicAuth("sans@test.fr")))
                .andExpect(status().isNotFound());
        // Anonyme : 401 (filtre, avant @PreAuthorize).
        mockMvc.perform(get("/api/creators/me"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- lecture enrichie

    @Test
    void lecture_slug_followers_et_liste() throws Exception {
        account("star@test.fr", "star");
        account("fan@test.fr", "fan");
        Long id = createChannel("star@test.fr", "star", "Star TV", null);
        // fan suit star (via follows).
        mockMvc.perform(post("/api/follows").header("Authorization", basicAuth("fan@test.fr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"followerId\":"
                                + userRepository.findByEmail("fan@test.fr").orElseThrow().getId()
                                + ",\"channelId\":" + id + "}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/creators/slug/star-tv"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1));
        mockMvc.perform(get("/api/creators/slug/inconnue")).andExpect(status().isNotFound());
    }

    // -------------------------------------------------- modification + IDOR + mass assignment

    @Test
    void update_owner_categorie_et_idor_403() throws Exception {
        account("owner@test.fr", "owner");
        account("autre@test.fr", "autre");
        Category tech = categoryRepository.save(Category.create("Tech", "tech", "Tech"));
        Long id = createChannel("owner@test.fr", "owner", "Owner TV", null);

        mockMvc.perform(put("/api/creators/" + id).header("Authorization", basicAuth("owner@test.fr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Owner Renommée\",\"primaryCategoryId\":" + tech.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("owner-renommee"))
                .andExpect(jsonPath("$.data.primaryCategory.slug").value("tech"));
        // IDOR : chaîne d'autrui → 403 (pas 404 : l'existence n'est pas cachée ici,
        // l'autorisation owner-or-admin est explicite et testée).
        mockMvc.perform(put("/api/creators/" + id).header("Authorization", basicAuth("autre@test.fr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Volée\"}"))
                .andExpect(status().isForbidden());
        // Anonyme → 401.
        mockMvc.perform(put("/api/creators/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Volée\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mass_assignment_role_status_ignore() throws Exception {
        account("clean@test.fr", "clean");
        Long id = createChannel("clean@test.fr", "clean", "Clean TV", null);
        mockMvc.perform(put("/api/creators/" + id).header("Authorization", basicAuth("clean@test.fr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Clean TV\",\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // -------------------------------------------------- avatar / bannière

    @Test
    void avatar_banniere_upload_liaison_remplacement() throws Exception {
        account("visuel@test.fr", "visuel");
        Long id = createChannel("visuel@test.fr", "visuel", "Visuel TV", null);
        String auth = basicAuth("visuel@test.fr");

        MvcResult avatar = mockMvc.perform(multipart("/api/creators/" + id + "/avatar").file(
                                new MockMultipartFile("file", "a.png", "image/png", bytes(PNG)))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").exists())
                .andReturn();
        String url1 = objectMapper.readTree(avatar.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();
        assertTrue(url1.startsWith("/uploads/avatars/"));
        Path file1 = STORAGE.resolve(url1.substring("/uploads/".length()));
        assertTrue(Files.exists(file1));

        mockMvc.perform(multipart("/api/creators/" + id + "/banner").file(
                                new MockMultipartFile("file", "b.png", "image/png", bytes(PNG)))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bannerUrl").value(
                        org.hamcrest.Matchers.startsWith("/uploads/banners/")));

        // Remplacement : nouvel avatar + ancien supprimé.
        MvcResult avatar2 = mockMvc.perform(multipart("/api/creators/" + id + "/avatar").file(
                                new MockMultipartFile("file", "a2.png", "image/png", bytes(PNG)))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn();
        String url2 = objectMapper.readTree(avatar2.getResponse().getContentAsString())
                .path("data").path("avatarUrl").asText();
        assertTrue(!url1.equals(url2));
        assertFalse(Files.exists(file1));
    }

    @Test
    void avatar_chaine_autrui_403_suspect_415_anonyme_401() throws Exception {
        account("prop@test.fr", "prop");
        account("intrus@test.fr", "intrus");
        Long id = createChannel("prop@test.fr", "prop", "Prop TV", null);
        // IDOR upload.
        mockMvc.perform(multipart("/api/creators/" + id + "/avatar").file(
                                new MockMultipartFile("file", "a.png", "image/png", bytes(PNG)))
                        .header("Authorization", basicAuth("intrus@test.fr")))
                .andExpect(status().isForbidden());
        // Contenu non-image.
        mockMvc.perform(multipart("/api/creators/" + id + "/avatar").file(
                                new MockMultipartFile("file", "evil.png", "image/png",
                                        "ceci n'est pas une image".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", basicAuth("prop@test.fr")))
                .andExpect(status().isUnsupportedMediaType());
        // Anonyme.
        mockMvc.perform(multipart("/api/creators/" + id + "/avatar").file(
                                new MockMultipartFile("file", "a.png", "image/png", bytes(PNG))))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------- fermeture logique

    @Test
    void fermeture_masque_public_preserve_contenus() throws Exception {
        account("ferme@test.fr", "ferme");
        account("fan2@test.fr", "fan2");
        Long id = createChannel("ferme@test.fr", "ferme", "Ferme TV", null);
        Stream live = streamRepository.save(Stream.schedule(
                creatorRepository.findById(id).orElseThrow(), "Live", null, java.time.Instant.now()));
        mockMvc.perform(post("/api/follows").header("Authorization", basicAuth("fan2@test.fr"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"followerId\":"
                                + userRepository.findByEmail("fan2@test.fr").orElseThrow().getId()
                                + ",\"channelId\":" + id + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/creators/" + id).header("Authorization", basicAuth("ferme@test.fr")))
                .andExpect(status().isOk());
        // Invisible en public (slug + id + listes), sans DELETE physique.
        mockMvc.perform(get("/api/creators/slug/ferme-tv")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/creators/" + id)).andExpect(status().isNotFound());
        assertTrue(creatorRepository.findById(id).isPresent());
        // Contenus intacts : stream + follow toujours là.
        assertTrue(streamRepository.findById(live.getId()).isPresent());
        assertEquals(1, followRepository.countByFollowedId(id));
        // Rétrogradation CREATOR → USER.
        assertEquals(User.Role.USER,
                userRepository.findByEmail("ferme@test.fr").orElseThrow().getRole());
    }
}
