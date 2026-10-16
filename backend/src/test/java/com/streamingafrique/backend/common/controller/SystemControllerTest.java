package com.streamingafrique.backend.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie automatiquement nos endpoints système : structure de réponse
 * cohérente (ApiResponse / ApiError) et codes HTTP corrects.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_renvoie_200_avec_structure_api_reponse() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Application opérationnelle"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("FLICK"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void info_renvoie_200_avec_donnees_generales() throws Exception {
        mockMvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Informations générales"))
                .andExpect(jsonPath("$.data.application").value("FLICK"))
                .andExpect(jsonPath("$.data.version").isNotEmpty());
    }

    @Test
    void route_inconnue_anonyme_renvoie_401_securise() throws Exception {
        // Depuis le socle sécurité, toute route hors liste blanche exige une
        // authentification — y compris les routes inconnues (on ne révèle pas
        // les URL protégées avec un 404). Anonyme → 401 JSON uniforme.
        mockMvc.perform(get("/api/inconnu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/inconnu"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}