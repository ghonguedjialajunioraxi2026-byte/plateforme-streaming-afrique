package com.streamingafrique.backend.payment.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.payment.dto.CreatePaymentRequest;
import com.streamingafrique.backend.payment.dto.PaymentResponse;
import com.streamingafrique.backend.payment.dto.SandboxSimulateRequest;
import com.streamingafrique.backend.payment.provider.PaymentProviderRegistry;
import com.streamingafrique.backend.payment.service.PaymentService;
import com.streamingafrique.backend.payment.service.PaymentWebhookService;
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
 * Controller REST des paiements (étape 14) :
 *   POST /api/payments                  → créer (201, AUTHENTICATED, idempotent)
 *   GET  /api/payments/mine             → MON historique paginé (AUTHENTICATED)
 *   GET  /api/payments/providers        → prestataires disponibles (AUTHENTICATED)
 *   GET  /api/payments/{id}             → détail (propriétaire ou ADMIN)
 *   POST /api/payments/{id}/cancel      → annuler (propriétaire ou ADMIN)
 *   GET  /api/payments/admin/all        → supervision paginée (ADMIN)
 *   POST /api/payments/sandbox/simulate → simuler une issue (sandbox, payeur/ADMIN)
 *
 * Le payeur est TOUJOURS le JWT (aucun userId frontend). Les webhooks vivent
 * dans {@link PaymentWebhookController} (public, signature HMAC).
 */
@Tag(name = "Paiements", description = "Achat de coins en monnaie fiat : "
        + "création idempotente (prix serveur), suivi, historique, supervision.")
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final PaymentWebhookService webhooks;
    private final PaymentProviderRegistry registry;

    public PaymentController(PaymentService payments, PaymentWebhookService webhooks,
                             PaymentProviderRegistry registry) {
        this.payments = payments;
        this.webhooks = webhooks;
        this.registry = registry;
    }

    @Operation(summary = "Créer un paiement",
            description = "AUTHENTICATED. Intention d'achat : le serveur relit le "
                    + "pack (prix/coins/devise) et génère la référence interne. "
                    + "Idempotent : même `idempotencyKey` = même paiement (201 "
                    + "initial, puis rejeu du même objet). Exemple : "
                    + "`{\"packageId\": 1, \"provider\": \"WAVE\", "
                    + "\"method\": \"MOBILE_MONEY\", \"idempotencyKey\": \"<uuid>\"}`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Achat",
                            value = "{\"packageId\": 1, \"provider\": \"WAVE\", "
                                    + "\"method\": \"MOBILE_MONEY\"}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "`PaymentResponse` + action (`paymentActionType`, "
                            + "`paymentInstructions` en sandbox)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Pack inexistant/désactivé, moyen non supporté",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Clé d'idempotence réutilisée pour une autre intention",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> create(
            @Valid @RequestBody CreatePaymentRequest request, Authentication authentication) {
        ApiResponse<PaymentResponse> body = ApiResponse.success(
                "Paiement initié", payments.createPayment(request, CurrentUser.from(authentication)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Mon historique de paiements",
            description = "AUTHENTICATED, paginé (récents d'abord).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `PaymentResponse`"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/mine")
    public ApiResponse<ApiPageResponse<PaymentResponse>> mine(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Mes paiements",
                payments.myHistory(CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Prestataires disponibles",
            description = "AUTHENTICATED. Catalogue EFFECTIF (prestataire + moyens "
                    + "supportés + drapeau sandbox) — le frontend n'affiche que ceux-ci.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Liste des prestataires disponibles"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/providers")
    public ApiResponse<List<PaymentProviderRegistry.ProviderInfo>> providers() {
        return ApiResponse.success("Prestataires disponibles", registry.catalog());
    }

    @Operation(summary = "Détail d'un paiement",
            description = "AUTHENTICATED : propriétaire ou ADMIN (anti IDOR). "
                    + "Le frontend sonde ce endpoint après l'action de paiement.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`PaymentResponse` (statut réel côté serveur)"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/{id}")
    public ApiResponse<PaymentResponse> get(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(payments.getPayment(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Annuler un paiement",
            description = "AUTHENTICATED : propriétaire ou ADMIN, avant confirmation "
                    + "uniquement (un paiement CONFIRMED ne s'annule plus).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`PaymentResponse` (CANCELLED ou état inchangé si terminal)"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping("/{id}/cancel")
    public ApiResponse<PaymentResponse> cancel(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Paiement annulé",
                payments.cancelPayment(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Supervision des paiements",
            description = "ADMIN : tous les paiements paginés (montant, devise, "
                    + "fournisseur, référence, statut, date). Lecture seule — "
                    + "JAMAIS de modification de l'historique financier.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `PaymentResponse`"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.ADMIN)
    @GetMapping("/admin/all")
    public ApiResponse<ApiPageResponse<PaymentResponse>> all(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Paiements",
                payments.allPayments(CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Simuler une issue (sandbox)",
            description = "Payeur ou ADMIN, sandbox ACTIF uniquement (404/403 "
                    + "sinon). Rejoue le pipeline webhook réel (signature interne, "
                    + "recoupement, crédit atomique) — développement et tests "
                    + "end-to-end sans prestataire réel.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Issue appliquée (`CONFIRMED`, `ALREADY_PROCESSED`…)"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping("/sandbox/simulate")
    public ApiResponse<String> simulate(@Valid @RequestBody SandboxSimulateRequest request,
                                        Authentication authentication) {
        PaymentWebhookService.WebhookOutcome outcome = webhooks.simulate(
                request.internalRef(), request.outcome(), CurrentUser.from(authentication));
        return ApiResponse.success("Simulation appliquée", outcome.name());
    }
}
