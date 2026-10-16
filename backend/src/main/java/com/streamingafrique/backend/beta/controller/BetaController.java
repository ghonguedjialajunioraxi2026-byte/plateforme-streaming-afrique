package com.streamingafrique.backend.beta.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.beta.dto.BetaDtos.BetaFeedbackDto;
import com.streamingafrique.backend.beta.dto.BetaDtos.SubmitFeedbackRequest;
import com.streamingafrique.backend.beta.service.BetaService;
import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Retours des bêta-testeurs (étape 24) — tout compte connecté.
 * Le rapporteur est TOUJOURS l'acteur authentifié (jamais du frontend).
 */
@Tag(name = "Bêta — retours", description = "Signalements des testeurs "
        + "(BUG/UX/SUGGESTION/…, contexte minimal non sensible).")
@SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
@RestController
@RequestMapping("/api/beta")
public class BetaController {

    private final BetaService beta;

    public BetaController(BetaService beta) {
        this.beta = beta;
    }

    @Operation(summary = "Signaler un retour",
            description = "Connecté. Catégorie + titre + description ; route, "
                    + "version et appareil optionnels (contexte non sensible).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Retour enregistré (`status: OPEN`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping("/feedback")
    public ResponseEntity<ApiResponse<BetaFeedbackDto>> submit(
            @Valid @RequestBody SubmitFeedbackRequest request,
            Authentication authentication) {
        ApiResponse<BetaFeedbackDto> body = ApiResponse.success("Retour enregistré, merci !",
                beta.submit(request, CurrentUser.from(authentication)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Mes retours",
            description = "Connecté. Uniquement SES signalements (jamais ceux d'autrui).")
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/feedback/mine")
    public ApiResponse<ApiPageResponse<BetaFeedbackDto>> mine(
            @PageableDefault(size = 20) Pageable pageable, Authentication authentication) {
        return ApiResponse.success("Mes retours",
                beta.myFeedback(pageable, CurrentUser.from(authentication)));
    }
}
