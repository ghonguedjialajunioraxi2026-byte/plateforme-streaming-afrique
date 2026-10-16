package com.streamingafrique.backend.beta.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.admin.service.AdminAuditService;
import com.streamingafrique.backend.beta.dto.BetaDtos.BetaFeedbackDto;
import com.streamingafrique.backend.beta.dto.BetaDtos.BetaInvitationDto;
import com.streamingafrique.backend.beta.dto.BetaDtos.CreateInvitationRequest;
import com.streamingafrique.backend.beta.dto.BetaDtos.InvitationCreatedResponse;
import com.streamingafrique.backend.beta.dto.BetaDtos.TriageFeedbackRequest;
import com.streamingafrique.backend.beta.model.BetaFeedback;
import com.streamingafrique.backend.beta.model.BetaInvitation;
import com.streamingafrique.backend.beta.service.BetaService;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Pilotage de la bêta privée (étape 24) — ADMIN uniquement, actes audités.
 *
 *   GET    /api/admin/beta/stats                  → compteurs réels
 *   POST   /api/admin/beta/invitations            → inviter (code montré UNE fois)
 *   GET    /api/admin/beta/invitations            → lister (filtre statut, jamais de code)
 *   POST   /api/admin/beta/invitations/{id}/revoke → révoquer
 *   GET    /api/admin/beta/feedback               → lister les retours (filtre statut)
 *   PUT    /api/admin/beta/feedback/{id}/triage   → statut + priorité P0..P3
 *
 * Suspension/retrait d'un testeur : statuts existants via
 * PATCH /api/admin/users/{id}/status (SUSPENDED/CLOSED) — aucun doublon.
 */
@Tag(name = "Administration — bêta", description = "Invitations, retours et "
        + "compteurs de la bêta privée. ADMIN uniquement, actes audités.")
@SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
@PreAuthorize(EndpointTier.ADMIN)
@RestController
@RequestMapping("/api/admin/beta")
public class BetaAdminController {

    private final BetaService beta;
    private final AdminAuditService acts;

    public BetaAdminController(BetaService beta, AdminAuditService acts) {
        this.beta = beta;
        this.acts = acts;
    }

    @GetMapping("/stats")
    public ApiResponse<BetaService.BetaStats> stats(Authentication authentication) {
        return ApiResponse.success("Bêta", beta.stats(CurrentUser.from(authentication)));
    }

    @PostMapping("/invitations")
    public ResponseEntity<ApiResponse<InvitationCreatedResponse>> invite(
            @Valid @RequestBody CreateInvitationRequest request, Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        InvitationCreatedResponse created = beta.invite(request.email(), request.note(), actor);
        acts.record(actor, "BETA_INVITED", "BETA_INVITATION", created.id(),
                null, request.email(), "Invitation bêta");
        ApiResponse<InvitationCreatedResponse> body =
                ApiResponse.success("Invitation créée — code à transmettre", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/invitations")
    public ApiResponse<ApiPageResponse<BetaInvitationDto>> invitations(
            @RequestParam(required = false) BetaInvitation.Status status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Invitations",
                beta.listInvitations(status, pageable, CurrentUser.from(authentication)));
    }

    @PostMapping("/invitations/{id}/revoke")
    public ApiResponse<BetaInvitationDto> revoke(@PathVariable Long id,
                                                 Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        BetaInvitationDto revoked = beta.revoke(id, actor);
        acts.record(actor, "BETA_INVITE_REVOKED", "BETA_INVITATION", id,
                "PENDING", "REVOKED", "Révocation bêta");
        return ApiResponse.success("Invitation révoquée", revoked);
    }

    @GetMapping("/feedback")
    public ApiResponse<ApiPageResponse<BetaFeedbackDto>> feedback(
            @RequestParam(required = false) BetaFeedback.Status status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Retours",
                beta.listFeedback(status, pageable, CurrentUser.from(authentication)));
    }

    @PutMapping("/feedback/{id}/triage")
    public ApiResponse<BetaFeedbackDto> triage(@PathVariable Long id,
                                               @Valid @RequestBody TriageFeedbackRequest request,
                                               Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        BetaFeedbackDto triaged = beta.triage(id, request.status(), request.priority(), actor);
        acts.record(actor, "BETA_FEEDBACK_TRIAGED", "BETA_FEEDBACK", id,
                null, request.status().name(), "Triage bêta");
        return ApiResponse.success("Retour trié", triaged);
    }
}
