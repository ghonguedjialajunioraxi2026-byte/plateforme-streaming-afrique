package com.streamingafrique.backend.moderation.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.moderation.dto.ChatMessageDto;
import com.streamingafrique.backend.moderation.dto.DecideActionRequest;
import com.streamingafrique.backend.moderation.dto.HideMessageRequest;
import com.streamingafrique.backend.moderation.dto.ModerationActionDto;
import com.streamingafrique.backend.moderation.dto.ModerationDashboardDto;
import com.streamingafrique.backend.moderation.dto.ReportDto;
import com.streamingafrique.backend.moderation.dto.UserSummaryDto;
import com.streamingafrique.backend.moderation.model.ModerationAction;
import com.streamingafrique.backend.moderation.model.ModerationReport;
import com.streamingafrique.backend.moderation.service.ChatModerationService;
import com.streamingafrique.backend.moderation.service.ReportService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Centre de modération (étape 17) — classe entière MODERATOR (hiérarchie :
 * ADMIN inclus).
 *
 * Endpoints :
 *   GET    /api/moderator/dashboard            → signalements ouverts, sanctions actives
 *   GET    /api/moderator/reports?status=      → file paginée
 *   GET    /api/moderator/reports/{id}         → détail
 *   POST   /api/moderator/reports/{id}/accept  → retient (audité)
 *   POST   /api/moderator/reports/{id}/reject  → classe sans suite (audité + notifié)
 *   POST   /api/moderator/reports/{id}/decide  → sanctionne (effets + notifications + audit)
 *   GET    /api/moderator/actions?state=       → historique des sanctions
 *   POST   /api/moderator/actions/{id}/lift    → lève (réactivation auto si dernière mesure)
 *   GET    /api/moderator/chat?streamId=       → historique d'un live
 *   POST   /api/moderator/chat/{id}/hide       → masque motivé (audité)
 *
 * La gestion des COMPTES (statut) et des RÔLES reste ADMIN (/api/admin).
 */
@Tag(name = "Modération", description = "File des signalements, sanctions et chat. "
        + "MODERATOR et ADMIN.")
@SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
@PreAuthorize(EndpointTier.MODERATOR)
@RestController
@RequestMapping("/api/moderator")
public class ModerationController {

    private final ReportService reports;
    private final ChatModerationService chat;

    public ModerationController(ReportService reports, ChatModerationService chat) {
        this.reports = reports;
        this.chat = chat;
    }

    @Operation(summary = "Vue d'ensemble modération",
            description = "File OPEN (première page) + compteurs ouverts/actifs.")
    @GetMapping("/dashboard")
    public ApiResponse<ModerationDashboardDto> overview(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        var open = reports.list(ModerationReport.Status.OPEN, actor, pageable);
        var active = reports.actions(ModerationAction.State.ACTIVE, actor,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return ApiResponse.success("Modération",
                new ModerationDashboardDto(open.totalElements(), active.totalElements(), open));
    }

    @Operation(summary = "File des signalements",
            description = "Paginée par statut (défaut OPEN).")
    @GetMapping("/reports")
    public ApiResponse<ApiPageResponse<ReportDto>> reports(
            @RequestParam(defaultValue = "OPEN") ModerationReport.Status status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Signalements",
                reports.list(status, CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Détail d'un signalement")
    @GetMapping("/reports/{id}")
    public ApiResponse<ReportDto> report(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(
                reports.get(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Retenir un signalement",
            description = "OPEN → ACCEPTED. Acte audité.")
    @PostMapping("/reports/{id}/accept")
    public ApiResponse<ReportDto> accept(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Signalement retenu",
                reports.accept(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Classer sans suite",
            description = "OPEN → REJECTED. Acte audité, auteur du signalement notifié.")
    @PostMapping("/reports/{id}/reject")
    public ApiResponse<ReportDto> reject(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Signalement classé",
                reports.reject(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Sanctionner",
            description = "Retient le signalement (s'il est OPEN) puis exécute la "
                    + "sanction : avertissement, masquage de contenu, suspension de "
                    + "compte ou de chaîne. Reporter et cible notifiés, acte audité. "
                    + "TIMEOUT et BAN_TEMP exigent `endsAt`. Exemple : "
                    + "`{\"action\":\"TIMEOUT\",\"scope\":\"ACCOUNT\","
                    + "\"targetUserId\":9,\"reason\":\"spam\","
                    + "\"endsAt\":\"2026-09-21T20:00:00Z\"}`.")
    @PostMapping("/reports/{id}/decide")
    public ResponseEntity<ApiResponse<ModerationActionDto>> decide(
            @PathVariable Long id, @Valid @RequestBody DecideActionRequest request,
            Authentication authentication) {
        ApiResponse<ModerationActionDto> body = ApiResponse.success("Sanction appliquée",
                reports.decide(id, request, CurrentUser.from(authentication)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Historique des sanctions",
            description = "Paginé, filtre d'état optionnel (ACTIVE, LIFTED, EXPIRED).")
    @GetMapping("/actions")
    public ApiResponse<ApiPageResponse<ModerationActionDto>> actions(
            @RequestParam(required = false) ModerationAction.State state,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Sanctions",
                reports.actions(state, CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Lever une sanction",
            description = "ACTIVE → LIFTED (audité). Réactive le compte si c'était "
                    + "la dernière mesure privative active le visant.")
    @PostMapping("/actions/{id}/lift")
    public ApiResponse<ModerationActionDto> lift(@PathVariable Long id,
                                                 Authentication authentication) {
        return ApiResponse.success("Sanction levée",
                reports.lift(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Historique chat d'un live",
            description = "Messages d'un direct, anciens d'abord, paginés.")
    @GetMapping("/chat")
    public ApiResponse<ApiPageResponse<ChatMessageDto>> chat(
            @Parameter(description = "Direct concerné") @RequestParam Long streamId,
            @PageableDefault(size = 50, sort = "id",
                    direction = Sort.Direction.ASC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Messages",
                chat.history(streamId, CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Masquer un message",
            description = "Motif obligatoire, acte audité CHAT_MESSAGE_HIDDEN. "
                    + "Idempotent (déjà masqué → succès). Le message reste pour dossier.")
    @PostMapping("/chat/{id}/hide")
    public ApiResponse<ChatMessageDto> hideMessage(
            @PathVariable Long id, @Valid @RequestBody HideMessageRequest request,
            Authentication authentication) {
        return ApiResponse.success("Message masqué",
                chat.hide(id, request.reason(), CurrentUser.from(authentication)));
    }

    @Operation(summary = "Fiche modérateur d'un compte",
            description = "Profil restreint (jamais d'email — réservé à l'ADMIN) + "
                    + "10 dernières sanctions.")
    @GetMapping("/users/{userId}/summary")
    public ApiResponse<UserSummaryDto> userSummary(
            @PathVariable Long userId, Authentication authentication) {
        return ApiResponse.success(
                reports.userSummary(userId, CurrentUser.from(authentication)));
    }
}
