package com.streamingafrique.backend.stream;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.stream.service.StreamKeyService;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garde d'ingest du serveur média (étape 23) — {@code POST /api/ingest/on-publish}.
 *
 * Le hook SRS est machine-à-machine : la preuve est le secret partagé
 * {@code ?secret=} (+ statut LIVE + clé de stream), jamais un JWT.
 * SRS coupe la publication sur tout statut non-2xx — chaque cas de refus
 * est donc un cas de sécurité réel (pas de squat, pas de clé devinée).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IngestAuthTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PASSWORD = "ingest-23";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StreamRepository streamRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StreamKeyService keyService;

    /** Secret partagé réel de la configuration (jamais en dur ici). */
    @Value("${app.streaming.ingest-secret}")
    private String ingestSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User user(String email) {
        User user = User.create(email, email, passwordEncoder.encode(PASSWORD));
        user.changeRole(User.Role.CREATOR);
        return userRepository.save(user);
    }

    private static String basic(User account) {
        String raw = account.getEmail() + ":" + PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
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
        return objectMapper.readTree(body).path("data").path("id").asLong();
    }

    private long schedule(User owner, long channelId) throws Exception {
        String body = mockMvc.perform(post("/api/streams")
                        .header("Authorization", basic(owner))
                        .param("creatorId", String.valueOf(channelId))
                        .contentType(JSON).content("""
                                { "title" : "Direct ingest" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return data.path("id").asLong();
    }

    private void start(User owner, long streamId) throws Exception {
        mockMvc.perform(post("/api/streams/{id}/start", streamId)
                        .header("Authorization", basic(owner)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Secret partagé invalide → 401, publication coupée")
    void secret_invalide_refuse() throws Exception {
        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", "mauvais-secret")
                        .param("stream", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Direct inexistant → 404")
    void direct_inexistant_introuvable() throws Exception {
        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .param("stream", "987654321"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Direct programmé (non LIVE) → 403, pas de squat avant démarrage")
    void direct_non_live_refuse() throws Exception {
        User owner = user("planifie-23@test.fr");
        long streamId = schedule(owner, createChannel(owner, "Planifié TV"));

        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .param("stream", String.valueOf(streamId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("LIVE sans clé attachée + bon secret → 200 (secret partagé = preuve)")
    void live_sans_cle_accepte_par_secret() throws Exception {
        User owner = user("live-23@test.fr");
        long streamId = schedule(owner, createChannel(owner, "Live TV"));
        start(owner, streamId);

        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .param("stream", String.valueOf(streamId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.streamId").value(streamId));
    }

    @Test
    @DisplayName("LIVE + clé attachée : mauvaise clé → 403, bonne clé → 200")
    void cle_attachee_verifiee() throws Exception {
        User owner = user("cle-23@test.fr");
        long streamId = schedule(owner, createChannel(owner, "Clé TV"));
        start(owner, streamId);

        String plainKey = keyService.generate();
        Stream stream = streamRepository.findById(streamId).orElseThrow();
        stream.attachIngest(keyService.hash(plainKey), Stream.IngestProtocol.RTMP,
                "http://hls/live/" + streamId + ".m3u8");
        streamRepository.save(stream);

        // Mauvaise clé (style SRS : stream + param séparés).
        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .param("stream", String.valueOf(streamId))
                        .param("param", "?key=sa_fausse-cle"))
                .andExpect(status().isForbidden());

        // Bonne clé (style OBS : tout dans le nom de stream).
        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .param("stream", streamId + "?key=" + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.streamId").value(streamId));
    }

    @Test
    @DisplayName("Nom de stream non numérique → 400 (jamais 500)")
    void stream_invalide_rejete_proprement() throws Exception {
        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .param("stream", "live///"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Hook SRS réel (corps JSON) : secret en query + stream/param en JSON → 200")
    void hook_srs_corps_json_accepte() throws Exception {
        User owner = user("srsjson-23@test.fr");
        long streamId = schedule(owner, createChannel(owner, "SRS JSON TV"));
        start(owner, streamId);

        String plainKey = keyService.generate();
        Stream stream = streamRepository.findById(streamId).orElseThrow();
        stream.attachIngest(keyService.hash(plainKey), Stream.IngestProtocol.RTMP,
                "http://hls/live/" + streamId + ".m3u8");
        streamRepository.save(stream);

        // Format exact envoyé par SRS : query ?secret=… + corps JSON.
        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .contentType(JSON).content("""
                                { "action" : "on_publish", "vhost" : "__defaultVhost__",
                                  "app" : "live", "stream" : "%d",
                                  "param" : "?key=%s" }
                                """.formatted(streamId, plainKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.streamId").value(streamId));

        // Même format, mauvaise clé → 403 (publication coupée).
        mockMvc.perform(post("/api/ingest/on-publish")
                        .param("secret", ingestSecret)
                        .contentType(JSON).content("""
                                { "action" : "on_publish", "app" : "live",
                                  "stream" : "%d", "param" : "?key=sa_fausse-cle" }
                                """.formatted(streamId)))
                .andExpect(status().isForbidden());
    }
}
