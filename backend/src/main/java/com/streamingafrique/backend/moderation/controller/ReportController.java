package com.streamingafrique.backend.moderation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.moderation.dto.FileReportRequest;
import com.streamingafrique.backend.moderation.dto.ReportDto;
import com.streamingafrique.backend.moderation.service.ReportService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Dépôt de signalements (étape 17) : tout compte connecté peut signaler un
 * utilisateur, une chaîne, un direct, une VOD, un clip ou un message.
 * Le traitement vit sur /api/moderator (MODERATOR+).
 */
@Tag(name = "Signalements", description = "Dépôt public authentifié : un signalement "
        + "par cible (type + id), motif et description.")
@SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
@PreAuthorize(EndpointTier.AUTHENTICATED)
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @Operation(summary = "Signaler un contenu ou un compte",
            description = "AUTHENTICATED. Exemple : `POST /api/reports` "
                    + "`{\"targetType\":\"CHAT_MESSAGE\",\"targetId\":12,"
                    + "\"reason\":\"SPAM\",\"description\":\"lien douteux\"}`. "
                    + "La cible est résolue côté serveur ; on ne signale pas "
                    + "son propre compte.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Signalement",
                            value = "{\"targetType\": \"CHAT_MESSAGE\", "
                                    + "\"targetId\": 12, \"reason\": \"SPAM\", "
                                    + "\"description\": \"lien douteux\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Signalement enregistré (statut OPEN)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR` ou cible invalide",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Cible inexistante",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ReportDto>> file(
            @Valid @RequestBody FileReportRequest request,
            Authentication authentication) {
        ApiResponse<ReportDto> body = ApiResponse.success("Signalement enregistré",
                reports.file(request, CurrentUser.from(authentication)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
