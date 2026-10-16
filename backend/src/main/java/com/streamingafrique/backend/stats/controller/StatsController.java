package com.streamingafrique.backend.stats.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.stats.dto.LiveStreamsCountDto;
import com.streamingafrique.backend.stats.service.StatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller REST des statistiques — endpoints :
 *   GET /api/stats/live-streams-count   → nombre de directs en cours
 *
 * Statistiques « courte durée » : le chiffre est calculé en base puis mis
 * en cache quelques secondes (TTL {@code stats-ttl}). La base reste la
 * source de vérité ; le cache évite un comptage à chaque requête.
 */
@Tag(name = "Statistiques", description = "Chiffres publics courte durée "
        + "(calcul en base + cache de quelques secondes).")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "Nombre de directs en cours",
            description = "PUBLIC. Pour le bandeau d'accueil du frontend.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`LiveStreamsCountDto` (compteur + horodatage de mesure)"))
    @GetMapping("/live-streams-count")
    public ApiResponse<LiveStreamsCountDto> liveStreamsCount() {
        return ApiResponse.success("Nombre de directs en cours",
                statsService.getLiveStreamsCount());
    }
}