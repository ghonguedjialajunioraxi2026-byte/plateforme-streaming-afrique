package com.streamingafrique.backend.creator.dashboard.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.creator.dashboard.dto.CreatorAnalyticsDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorCommunityDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorDashboardDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorModerationDto;
import com.streamingafrique.backend.creator.dashboard.dto.CreatorRevenueDto;
import com.streamingafrique.backend.creator.dashboard.service.DashboardService;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Creator Studio — agrégats du dashboard (étape 16).
 *
 * Endpoints :
 *   GET /api/creator/dashboard   → vue d'ensemble (cartes, live, activité)
 *   GET /api/creator/analytics   → totaux + courbes + top contenus (?days=1..90)
 *   GET /api/creator/revenue     → soldes coins + totaux par source + journal
 *   GET /api/creator/community   → followers + abonnés récents
 *   GET /api/creator/moderation  → signalements ouverts visant la chaîne
 *
 * SÉCURITÉ (§17) : classe entière AUTHENTICATED ; la chaîne est résolue
 * depuis le JWT dans le service — AUCUN paramètre creatorId/userId n'existe,
 * l'IDOR est impossible par construction. Sans chaîne → 404 guidant vers
 * la création (même API que /creator/settings).
 */
@Tag(name = "Creator Studio", description = "Dashboard créateur : agrégats de la chaîne "
        + "du connecté (live, stats, revenus, communauté, modération).")
@SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
@PreAuthorize(EndpointTier.AUTHENTICATED)
@RestController
@RequestMapping("/api/creator")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @Operation(summary = "Vue d'ensemble du studio",
            description = "Cartes (followers, abonnés, VOD/vues, clips/vues, cadeaux), "
                    + "direct en cours (viewers Redis temps réel), soldes coins, "
                    + "5 dernières notifications.")
    @GetMapping("/dashboard")
    public ApiResponse<CreatorDashboardDto> overview(Authentication authentication) {
        return ApiResponse.success("Tableau de bord",
                dashboard.overview(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Analytique de la chaine",
            description = "Totaux, serie temporelle des snapshots, top 5 VOD et top 5 clips.")
    @GetMapping("/analytics")
    public ApiResponse<CreatorAnalyticsDto> analytics(
            @Parameter(description = "Fenetre en jours, entre 1 et 90 (defaut 30)")
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {
        return ApiResponse.success("Analytique",
                dashboard.analytics(CurrentUser.from(authentication), days));
    }

    @Operation(summary = "Revenus de la chaîne",
            description = "Soldes coins (total, réservé, disponible), totaux reçus par "
                    + "source (cadeaux, abonnements : voir le journal des transactions), "
                    + "10 derniers mouvements avec références fiat d'origine.")
    @GetMapping("/revenue")
    public ApiResponse<CreatorRevenueDto> revenue(Authentication authentication) {
        return ApiResponse.success("Revenus",
                dashboard.revenue(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Communauté de la chaîne",
            description = "Compteurs + 10 derniers followers et 10 derniers abonnés "
                    + "payants (profils publics uniquement, jamais d'email).")
    @GetMapping("/community")
    public ApiResponse<CreatorCommunityDto> community(Authentication authentication) {
        return ApiResponse.success("Communauté",
                dashboard.community(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Modération de son espace",
            description = "Signalements OUverts visant directement la chaîne "
                    + "(périmètre du créateur uniquement, pas la file globale).")
    @GetMapping("/moderation")
    public ApiResponse<CreatorModerationDto> moderation(Authentication authentication) {
        return ApiResponse.success("Modération",
                dashboard.moderation(CurrentUser.from(authentication)));
    }
}
