package com.streamingafrique.backend.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que chaque catégorie d'erreur renvoie une réponse JSON uniforme
 * (ApiError : success, status, errorCode, message, path, timestamp).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorDemoControllerTest {

    private static final String BASE = "/api/test/errors";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void erreur_metier_not_found_renvoie_404() throws Exception {
        mockMvc.perform(get(BASE + "/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ressource introuvable"))
                .andExpect(jsonPath("$.path").value(BASE + "/not-found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void erreur_metier_conflict_renvoie_409() throws Exception {
        mockMvc.perform(get(BASE + "/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void erreur_metier_bad_request_renvoie_400() throws Exception {
        mockMvc.perform(get(BASE + "/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
    }

    @Test
    void erreur_metier_forbidden_renvoie_403() throws Exception {
        mockMvc.perform(get(BASE + "/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void validation_dto_invalide_renvoie_400_validation_error() throws Exception {
        mockMvc.perform(post(BASE + "/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").value("Le champ name est obligatoire"));
    }

    @Test
    void validation_plusieurs_champs_renvoie_la_map_des_erreurs() throws Exception {
        String corpsInvalide = """
                {
                  "username": "ab",
                  "email": "mauvais",
                  "password": "abc",
                  "age": 12,
                  "acceptedTerms": true
                }
                """;

        mockMvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsInvalide))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.username").value("Le nom d'utilisateur doit contenir entre 3 et 20 caractères"))
                .andExpect(jsonPath("$.errors.email").value("Adresse email invalide"))
                .andExpect(jsonPath("$.errors.password").value(org.hamcrest.Matchers.containsString("entre 8 et 100")))
                .andExpect(jsonPath("$.errors.age").value("L'âge minimum est 18 ans"));
    }

    @Test
    void validation_avec_corps_valide_renvoie_200() throws Exception {
        String corpsValide = """
                {
                  "username": "diarra",
                  "email": "diarra@example.com",
                  "password": "motdepasse123",
                  "age": 25,
                  "acceptedTerms": true
                }
                """;

        mockMvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsValide))
                .andExpect(status().isOk());
    }

    @Test
    void mauvais_type_parametre_renvoie_400_bad_param() throws Exception {
        mockMvc.perform(get(BASE + "/bad-param/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_PARAM"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("id")));
    }

    @Test
    void parametre_manquant_renvoie_400_missing_param() throws Exception {
        mockMvc.perform(get(BASE + "/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAM"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("q")));
    }

    @Test
    void methode_non_supportee_renvoie_405() throws Exception {
        mockMvc.perform(post(BASE + "/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void erreur_interne_renvoie_500_sans_detail_fuite() throws Exception {
        mockMvc.perform(get(BASE + "/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Erreur interne du serveur"));
    }
}