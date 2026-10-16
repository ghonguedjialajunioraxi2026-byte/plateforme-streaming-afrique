package com.streamingafrique.backend.common.controller;

import com.streamingafrique.backend.common.dto.HealthDto;
import com.streamingafrique.backend.common.dto.InfoDto;
import com.streamingafrique.backend.common.service.SystemService;
import com.streamingafrique.backend.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints système de la plateforme.
 * Aucune logique métier ici : on délègue au service.
 */
@Tag(name = "Système", description = "Sondes publiques (PUBLIC) : état et informations non sensibles.")
@RestController
@RequestMapping("/api")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    /**
     * GET /api/health — vérifie que l'application répond.
     * Réponse 200 : { success, message, data: {status, application}, timestamp }
     */
    @Operation(summary = "État de l'application",
            description = "PUBLIC, jamais limitée (rate limiting exclu). Répond toujours "
                    + "`200` avec `data: {status:\"UP\", application:\"FLICK\"}`. "
                    + "Chaque réponse porte `X-Request-Id`.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Application opérationnelle"))
    @GetMapping("/health")
    public ApiResponse<HealthDto> health() {
        return ApiResponse.success("Application opérationnelle", systemService.health());
    }

    /**
     * GET /api/info — informations générales non sensibles.
     */
    @Operation(summary = "Informations générales",
            description = "PUBLIC, jamais limitée. Métadonnées non sensibles "
                    + "(nom, description, version).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Informations générales"))
    @GetMapping("/info")
    public ApiResponse<InfoDto> info() {
        return ApiResponse.success("Informations générales", systemService.info());
    }
}