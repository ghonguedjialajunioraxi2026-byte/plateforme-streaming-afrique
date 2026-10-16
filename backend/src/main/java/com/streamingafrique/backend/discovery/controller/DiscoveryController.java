package com.streamingafrique.backend.discovery.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.discovery.dto.TrendingResponse;
import com.streamingafrique.backend.discovery.service.DiscoveryService;
import com.streamingafrique.backend.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Découverte Africa+ PUBLIQUE (étape 19) — tendances et émergents.
 *
 * Filtres issus des DONNÉES (pays déclarés, langues déclarées) : aucun
 * pays hard-codé. Score transparent documenté dans le service.
 */
@Tag(name = "Découverte Africa+",
        description = "Tendances (tous pays / par pays) et créateurs "
                + "émergents. Lecture publique, résultats cachés 2 min.")
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryService discovery;

    public DiscoveryController(DiscoveryService discovery) {
        this.discovery = discovery;
    }

    @Operation(summary = "Tendances",
            description = "Lives + VOD classés par score (statut, audience, "
                    + "récence). Filtres optionnels : `country` (ISO alpha-2, "
                    + "404 si inconnu), `language` (fr, en, wo…​), "
                    + "`category` (slug). Ex. `?country=NG&language=en`.")
    @GetMapping("/trending")
    public ApiResponse<TrendingResponse> trending(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Tendances",
                discovery.trending(country, language, category, size));
    }

    @Operation(summary = "Tendances d'un pays",
            description = "Raccourci `trending?country={code}` (404 si inconnu).")
    @GetMapping("/trending/{country}")
    public ApiResponse<TrendingResponse> trendingByCountry(
            @PathVariable String country,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success("Tendances " + country.toUpperCase(),
                discovery.trending(country, language, category, size));
    }

    @Operation(summary = "Créateurs émergents",
            description = "Chaînes ACTIVES gagnant des followers (≥ 2 sur "
                    + "30 j, récents d'abord). Filtre `country` optionnel. "
                    + "Aucune promesse de popularité future.")
    @GetMapping("/creators/emerging")
    public ApiResponse<List<CreatorProfileDto>> emerging(
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "10") int size) {
        if (country != null && !country.isBlank()) {
            return ApiResponse.success("Créateurs émergents",
                    discovery.emerging(country, size));
        }
        return ApiResponse.success("Créateurs émergents",
                discovery.emerging(null, size));
    }
}
