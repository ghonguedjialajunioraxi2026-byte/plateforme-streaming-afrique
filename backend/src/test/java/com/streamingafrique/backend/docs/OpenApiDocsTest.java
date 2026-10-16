package com.streamingafrique.backend.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stratégie de tests (étape 6.29, prolongée 6.30) — la documentation OpenAPI
 * est générée depuis le code : ce test verrouille qu'elle reste EXACTE.
 *
 *   - la spécification est servie sans authentification (le frontend et les
 *     générateurs de client doivent y accéder librement) ;
 *   - chaque route RÉELLE y figure (échantillon par domaine) ;
 *   - AUCUNE fausse route n'y figure (ex. {@code /api/search} n'existe dans
 *     aucun contrôleur : seule la recherche {@code ?search=} sur les listes
 *     existe — la documenter comme endpoint serait mentir au frontend).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("La spécification OpenAPI est publique et versionnée")
    void spec_publique() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isString())
                .andExpect(jsonPath("$.info.title").value("FLICK — API REST"));
    }

    @Test
    @DisplayName("Les routes réelles sont documentées, sans fausse route")
    void routes_reelles_sans_fausse_route() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // Échantillon réel, un par domaine.
                .andExpect(jsonPath("$.paths", hasKey("/api/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/api/users")))
                .andExpect(jsonPath("$.paths", hasKey("/api/users/{id}")))
                .andExpect(jsonPath("$.paths", hasKey("/api/creators")))
                .andExpect(jsonPath("$.paths", hasKey("/api/streams")))
                .andExpect(jsonPath("$.paths", hasKey("/api/streams/{id}/start")))
                .andExpect(jsonPath("$.paths", hasKey("/api/videos/feed")))
                .andExpect(jsonPath("$.paths", hasKey("/api/videos/{id}/visibility")))
                .andExpect(jsonPath("$.paths", hasKey("/api/clips/from-stream")))
                .andExpect(jsonPath("$.paths", hasKey("/api/clips/from-video")))
                .andExpect(jsonPath("$.paths", hasKey("/api/follows")))
                .andExpect(jsonPath("$.paths", hasKey("/api/notifications")))
                .andExpect(jsonPath("$.paths", hasKey("/api/uploads/images")))
                .andExpect(jsonPath("$.paths", hasKey("/api/presence/online")))
                .andExpect(jsonPath("$.paths", hasKey("/api/stats/live-streams-count")))
                .andExpect(jsonPath("$.paths", hasKey("/api/events/publish")))
                .andExpect(jsonPath("$.paths", hasKey("/api/health")))
                // Fausses routes : AUCUN contrôleur ne les sert.
                .andExpect(jsonPath("$.paths", not(hasKey("/api/search"))))
                .andExpect(jsonPath("$.paths", not(hasKey("/api/login"))));
    }

    @Test
    @DisplayName("Les schémas de sécurité JWT + Basic sont déclarés")
    void schemas_securite() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("bearerAuth")))
                .andExpect(jsonPath("$.components.securitySchemes", hasKey("basicAuth")));
    }

    @Test
    @DisplayName("L'interface Swagger UI est servie sans authentification")
    void swagger_ui_publique() throws Exception {
        // /swagger-ui.html redirige vers l'interface ; l'index est servi en 200.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
