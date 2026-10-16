package com.streamingafrique.backend.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste la montée en charge « étape 6.16 » : architecture pagination + tri +
 * recherche + filtrage, par combinaison de Specifications Spring Data.
 *
 * Chaque scénario part de zéro (base H2, transaction annulée en fin de test)
 * et passe par le chemin HTTP complet : GET → Controller → Service → Base.
 *
 * Autorisation (étape 6.25) : les écritures sont AUTHENTIFIÉES en Basic
 * (comptes créés directement en base avec mot de passe), et les catégories
 * sont créées par un ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchFilterSortTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "motdepasse-6.26";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    // -------------------------------------------------- helpers

    /** Compte avec mot de passe, créé directement en base (setup de test). */
    private User user(String email, User.Role role) {
        User user = User.create(email, email, passwordEncoder.encode(PASSWORD));
        user.changeRole(role);
        return userRepository.save(user);
    }

    private static String basic(User account) {
        String raw = account.getEmail() + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private long read(String pointer, String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        for (String part : pointer.split("\\.")) {
            node = node.path(part);
        }
        return node.asLong();
    }

    private long createChannel(User owner, String name) throws Exception {
        String body = mockMvc.perform(post("/api/creators")
                        .header("Authorization", basic(owner))
                        .param("userId", String.valueOf(owner.getId()))
                        .contentType(JSON).content("""
                                { "displayName" : "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    private long createCategory(User admin, String name) throws Exception {
        String body = mockMvc.perform(post("/api/categories")
                        .header("Authorization", basic(admin))
                        .contentType(JSON).content("""
                                { "name" : "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    private long createStream(User owner, long creatorId, Long categoryId, String title) throws Exception {
        String json = categoryId == null
                ? """
                        { "title" : "%s" }
                        """.formatted(title)
                : """
                        { "title" : "%s", "categoryId" : %d }
                        """.formatted(title, categoryId);
        String body = mockMvc.perform(post("/api/streams")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(creatorId))
                        .contentType(JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    private void startStream(User owner, long streamId) throws Exception {
        mockMvc.perform(post("/api/streams/{id}/start", streamId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LIVE"));
    }

    private long publishVideo(User owner, long creatorId, Long categoryId, String title) throws Exception {
        String json = categoryId == null
                ? """
                        { "title" : "%s", "videoUrl" : "https://cdn.example.com/v.mp4", "durationSeconds" : 300 }
                        """.formatted(title)
                : """
                        { "title" : "%s", "categoryId" : %d, "videoUrl" : "https://cdn.example.com/v.mp4", "durationSeconds" : 300 }
                        """.formatted(title, categoryId);
        String body = mockMvc.perform(post("/api/videos")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(creatorId))
                        .contentType(JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    private long createClip(User owner, long creatorId, long streamId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/clips/from-stream")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(creatorId))
                        .param("streamId", String.valueOf(streamId))
                        .contentType(JSON).content("""
                                { "title" : "%s", "clipUrl" : "https://cdn.example.com/c.mp4", "durationSeconds" : 25 }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return read("data.id", body);
    }

    // -------------------------------------------------- pagination + tri

    @Test
    void pagination_enveloppe_complete_et_tri() throws Exception {
        User owner = user("page@example.com", User.Role.CREATOR);
        long channel = createChannel(owner, "Gaming TV");
        createStream(owner, channel, null, "Tournoi FIFA");
        createStream(owner, channel, null, "Direct chill");
        createStream(owner, channel, null, "Match amical");

        mockMvc.perform(get("/api/streams")
                        .param("page", "0").param("size", "2")
                        .param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.numberOfElements").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.content[0].title").value("Direct chill"))
                .andExpect(jsonPath("$.data.content[1].title").value("Match amical"));

        // page suivante → last = true, taille partielle
        mockMvc.perform(get("/api/streams")
                        .param("page", "1").param("size", "2")
                        .param("sort", "title,asc"))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.data.numberOfElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Tournoi FIFA"));
    }

    @Test
    void tri_invalide_renvoie_400_sort_invalid() throws Exception {
        mockMvc.perform(get("/api/streams").param("sort", "proprieteInexistante,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SORT_INVALID"));
    }

    // -------------------------------------------------- recherche

    @Test
    void recherche_insensible_a_la_casse_et_wildcards_echappes() throws Exception {
        User owner = user("search@example.com", User.Role.CREATOR);
        User admin = user("admin-search@example.com", User.Role.ADMIN);
        long channel = createChannel(owner, "Gaming TV");
        long gaming = createCategory(admin, "Gaming");
        createStream(owner, channel, gaming, "Tournoi FIFA");

        // « contient » insensible à la casse sur le titre
        mockMvc.perform(get("/api/streams").param("search", "fifa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Tournoi FIFA"));

        mockMvc.perform(get("/api/streams").param("search", "TOURNOI"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // aucun résultat → page vide mais 200
        mockMvc.perform(get("/api/streams").param("search", "zoulou"))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // le % saisi est littéral (échappé), pas un joker SQL
        mockMvc.perform(get("/api/categories").param("search", "gam%"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/categories").param("search", "gam"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // -------------------------------------------------- filtres combinés

    @Test
    void filtres_combines_categorie_statut_et_aliases() throws Exception {
        User owner = user("filtres@example.com", User.Role.CREATOR);
        User admin = user("admin-filtres@example.com", User.Role.ADMIN);
        long channel = createChannel(owner, "Gaming TV");
        long gaming = createCategory(admin, "Gaming");
        long musique = createCategory(admin, "Musique");

        long finale = createStream(owner, channel, gaming, "Finale esport");
        startStream(owner, finale);                                  // LIVE
        createStream(owner, channel, gaming, "Rebroadcast");         // SCHEDULED
        createStream(owner, channel, musique, "Concert en ligne");   // SCHEDULED

        // filtre par slug de catégorie (?category=gaming)
        mockMvc.perform(get("/api/streams").param("category", "gaming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // alias historique : ?categoryId=N
        mockMvc.perform(get("/api/streams").param("categoryId", String.valueOf(gaming)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // combinaison AND : catégorie + statut
        mockMvc.perform(get("/api/streams").param("category", "gaming").param("status", "LIVE"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Finale esport"));

        // combinaison AND : catégorie + recherche
        mockMvc.perform(get("/api/streams").param("category", "gaming").param("search", "rebroadcast"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // par créateur
        mockMvc.perform(get("/api/streams").param("creatorId", String.valueOf(channel)))
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // id inconnu → 0 résultat, jamais d'erreur
        mockMvc.perform(get("/api/streams").param("creatorId", "999999"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // -------------------------------------------------- vidéos

    @Test
    void videos_recherche_visibilite_et_feed_par_categorie() throws Exception {
        User owner = user("video@example.com", User.Role.CREATOR);
        User admin = user("admin-video@example.com", User.Role.ADMIN);
        long channel = createChannel(owner, "VOD TV");
        long gaming = createCategory(admin, "Gaming");

        publishVideo(owner, channel, gaming, "Tuto Spring Data");
        long secret = publishVideo(owner, channel, gaming, "Coulisses non publiées");
        mockMvc.perform(patch("/api/videos/{id}/visibility", secret)
                        .header("Authorization", basic(owner))
                        .contentType(JSON).content("""
                                { "visibility" : "PRIVATE" }
                                """))
                .andExpect(status().isOk());

        // recherche titre OU description
        mockMvc.perform(get("/api/videos").param("search", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // filtre catégorie via slug : le PUBLIC ne voit que la vidéo PUBLIC
        // (étape 15 : la PRIVATE n'apparaît plus aux anonymes — existence masquée)
        mockMvc.perform(get("/api/videos").param("category", "gaming"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // filtre visibilité : PRIVATE ignoré pour les anonymes (PUBLIC forcé)
        mockMvc.perform(get("/api/videos").param("visibility", "PRIVATE"))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // le feed n'expose QUE les vidéos PUBLIC, et accepte recherche + catégorie
        mockMvc.perform(get("/api/videos/feed"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/videos/feed").param("category", "gaming").param("search", "tuto"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/videos/feed").param("search", "coulisses"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // -------------------------------------------------- créateurs & clips

    @Test
    void createurs_et_clips_recherche() throws Exception {
        User ada = user("ada@example.com", User.Role.CREATOR);
        long adaChannel = createChannel(ada, "Ada & machines");
        User grace = user("grace@example.com", User.Role.CREATOR);
        createChannel(grace, "Grace Coding");

        // recherche sur le nom de la chaîne (insensible casse)
        mockMvc.perform(get("/api/creators").param("search", "ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].slug").value("ada-machines"));

        mockMvc.perform(get("/api/creators").param("search", "coding"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // clips : recherche sur le titre, brute ou via le feed public
        long streamId = createStream(ada, adaChannel, null, "Grand direct");
        createClip(ada, adaChannel, streamId, "Meilleur moment");
        createClip(ada, adaChannel, streamId, "Raté mémorable");

        mockMvc.perform(get("/api/clips").param("search", "moment"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/clips").param("search", "mémorable"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/videos").param("creatorId", String.valueOf(adaChannel)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}