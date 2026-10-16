package com.streamingafrique.backend.admin.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.admin.dto.AdminDashboardDto;
import com.streamingafrique.backend.admin.dto.AdminUserDto;
import com.streamingafrique.backend.admin.dto.AuditLogDto;
import com.streamingafrique.backend.admin.dto.ChainModerationRequest;
import com.streamingafrique.backend.admin.dto.ConfigDto;
import com.streamingafrique.backend.admin.dto.NotifyRequest;
import com.streamingafrique.backend.admin.dto.UserStatusRequest;
import com.streamingafrique.backend.admin.repository.AdminAuditLogRepository;
import com.streamingafrique.backend.admin.service.AdminAuditService;
import com.streamingafrique.backend.admin.service.AdminConfigService;
import com.streamingafrique.backend.admin.service.AdminDashboardService;
import com.streamingafrique.backend.admin.service.AdminUserService;
import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.service.CreatorService;
import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;
import com.streamingafrique.backend.stream.service.StreamService;
import com.streamingafrique.backend.user.model.User;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Administration plateforme (étape 17) — classe entière ADMIN.
 *
 * Endpoints :
 *   GET    /api/admin/dashboard            → compteurs plateforme
 *   GET    /api/admin/users                → recherche comptes (texte, rôle, statut)
 *   PATCH  /api/admin/users/{id}/status    → suspendre / réactiver / fermer (motif + audit)
 *   GET    /api/admin/creators             → recherche chaînes (texte, statut)
 *   POST   /api/admin/creators/{id}/suspend|reactivate → modération de chaîne (audit)
 *   POST   /api/admin/streams/{id}/stop    → arrêter un LIVE (VOD auto + audit)
 *   GET    /api/admin/audit                → journal d'audit (filtres action/acteur)
 *   GET    /api/admin/config               → paramètres courants
 *   PUT    /api/admin/config               → nouvelle version de paramètre (audit)
 *   POST   /api/admin/notifications        → notification ciblée (audit)
 *
 * Le changement de RÔLE reste sur PUT /api/users/{id}/role (ADMIN).
 * Les contenus (VOD/clips) se modèrent via leurs routes admin existantes
 * (.../admin/all + visibilité/suppression, gardes owner-or-ADMIN).
 */
@Tag(name = "Administration", description = "Pilotage plateforme : comptes, chaînes, "
        + "directs, audit, configuration, notifications. ADMIN uniquement.")
@SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
@PreAuthorize(EndpointTier.ADMIN)
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminDashboardService dashboard;
    private final AdminUserService adminUsers;
    private final CreatorService creators;
    private final StreamService streams;
    private final AdminAuditLogRepository audit;
    private final AdminAuditService acts;
    private final AdminConfigService config;

    public AdminController(AdminDashboardService dashboard,
                           AdminUserService adminUsers,
                           CreatorService creators,
                           StreamService streams,
                           AdminAuditLogRepository audit,
                           AdminAuditService acts,
                           AdminConfigService config) {
        this.dashboard = dashboard;
        this.adminUsers = adminUsers;
        this.creators = creators;
        this.streams = streams;
        this.audit = audit;
        this.acts = acts;
        this.config = config;
    }

    @Operation(summary = "Vue d'ensemble plateforme",
            description = "Compteurs SQL : comptes, chaînes, lives, contenus, paiements "
                    + "par statut, signalements ouverts, actes d'audit.")
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardDto> overview() {
        return ApiResponse.success("Pilotage", dashboard.overview());
    }

    @Operation(summary = "Rechercher des comptes",
            description = "Texte (email, pseudo, nom) + filtres rôle/statut, paginé. "
                    + "Jamais de secret exposé.")
    @GetMapping("/users")
    public ApiResponse<ApiPageResponse<AdminUserDto>> users(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) User.Status status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Comptes",
                adminUsers.search(search, role, status,
                        CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Changer le statut d'un compte",
            description = "SUSPENDED (sanction), ACTIVE (réactivation), CLOSED (fermeture). "
                    + "Motif obligatoire, acte audité, compte notifié. Jamais sur "
                    + "soi-même ni sur un ADMIN.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`AdminUserDto` avec le nouveau statut"))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Soi-même ou un ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserDto> userStatus(
            @PathVariable Long id, @Valid @RequestBody UserStatusRequest request,
            Authentication authentication) {
        return ApiResponse.success("Statut modifié",
                adminUsers.changeStatus(id, request.status(), request.reason(),
                        CurrentUser.from(authentication)));
    }

    @Operation(summary = "Rechercher des chaînes",
            description = "Texte (nom, slug) + statut (CLOSED incluses — le public les "
                    + "exclut), paginé.")
    @GetMapping("/creators")
    public ApiResponse<ApiPageResponse<CreatorProfileDto>> creators(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CreatorProfile.Status status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Chaînes",
                creators.searchAdmin(search, status, pageable));
    }

    @Operation(summary = "Suspendre une chaîne",
            description = "ACTIVE → SUSPENDED (motif + audit). Contenus intacts, "
                    + "réactivation possible. Jamais sa propre chaîne ici.")
    @PostMapping("/creators/{id}/suspend")
    public ApiResponse<CreatorProfileDto> suspendCreator(
            @PathVariable Long id, @Valid @RequestBody ChainModerationRequest request,
            Authentication authentication) {
        return ApiResponse.success("Chaîne suspendue",
                creators.suspend(id, request.reason(), CurrentUser.from(authentication)));
    }

    @Operation(summary = "Réactiver une chaîne",
            description = "SUSPENDED → ACTIVE (motif + audit).")
    @PostMapping("/creators/{id}/reactivate")
    public ApiResponse<CreatorProfileDto> reactivateCreator(
            @PathVariable Long id, @Valid @RequestBody ChainModerationRequest request,
            Authentication authentication) {
        return ApiResponse.success("Chaîne réactivée",
                creators.reactivate(id, request.reason(), CurrentUser.from(authentication)));
    }

    @Operation(summary = "Arrêter un direct",
            description = "Termine un LIVE (garde owner-or-ADMIN du service : l'ADMIN "
                    + "passe) + VOD automatique + acte audité STREAM_STOPPED.")
    @PostMapping("/streams/{id}/stop")
    public ApiResponse<Void> stopStream(@PathVariable Long id, Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        streams.end(id, actor);
        acts.record(actor, "STREAM_STOPPED", "STREAM", id, "LIVE", "ENDED",
                "Arrêt administratif");
        return ApiResponse.success("Direct arrêté", null);
    }

    @Operation(summary = "Journal d'audit",
            description = "Actes administratifs et de modération, récents d'abord. "
                    + "Filtres action/acteur optionnels. Append-only, jamais de secret.")
    @GetMapping("/audit")
    public ApiResponse<ApiPageResponse<AuditLogDto>> audit(
            @RequestParam(required = false) String action,
            @Parameter(description = "Acteur (id de compte)") @RequestParam(required = false) Long actorId,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        var page = audit.search(action, actorId, pageable);
        return ApiResponse.success("Audit",
                ApiPageResponse.from(page,
                        page.getContent().stream().map(AuditLogDto::from).toList()));
    }

    @Operation(summary = "Paramètres courants",
            description = "Une valeur par clé (version max).")
    @GetMapping("/config")
    public ApiResponse<ApiPageResponse<ConfigDto>> configs(
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Configuration",
                config.current(CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Régler un paramètre",
            description = "Crée une NOUVELLE version (jamais d'UPDATE, historique "
                    + "complet) + acte audité CONFIG_CHANGED.")
    @PutMapping("/config")
    public ApiResponse<ConfigDto> setConfig(
            @Valid @RequestBody ConfigDto.SetConfigRequest request,
            Authentication authentication) {
        return ApiResponse.success("Paramètre enregistré",
                config.set(request.key(), request.value(),
                        CurrentUser.from(authentication)));
    }

    @Operation(summary = "Notifier des utilisateurs",
            description = "Destinataires explicites (100 max), jamais de diffusion "
                    + "aveugle. Acte audité USERS_NOTIFIED.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Nombre de destinataires notifiés dans `data`"))
    @PostMapping("/notifications")
    public ResponseEntity<ApiResponse<Integer>> notify(
            @Valid @RequestBody NotifyRequest request,
            Authentication authentication) {
        int sent = adminUsers.notifyUsers(request.userIds(), request.type(),
                request.title(), request.message(), request.linkUrl(),
                CurrentUser.from(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Notifications envoyées", sent));
    }
}
