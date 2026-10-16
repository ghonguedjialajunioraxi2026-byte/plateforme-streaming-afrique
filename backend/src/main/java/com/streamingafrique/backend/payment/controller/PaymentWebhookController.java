package com.streamingafrique.backend.payment.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.payment.service.PaymentWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Webhooks prestataires (étape 14, §21) :
 *   POST /api/payments/webhooks/{provider} → PUBLIC (la SIGNATURE HMAC est
 *   l'authentification : sans signature valide, 401 et RIEN n'est persisté).
 *
 * Le corps est lu BRUT (signature calculée sur l'octet exact reçu). Répond
 * toujours 200 après traitement accepté — y compris "déjà traité" (le
 * prestataire cesse alors ses retries).
 */
@Tag(name = "Webhooks paiements", description = "Confirmation prestataire : "
        + "signature HMAC vérifiée, montants recoupés, crédit atomique unique.")
@RestController
@RequestMapping("/api/payments/webhooks")
public class PaymentWebhookController {

    private final PaymentWebhookService webhooks;

    public PaymentWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @Operation(summary = "Recevoir un webhook prestataire",
            description = "PUBLIC (signature = preuve). En-tête "
                    + "`X-Webhook-Signature` : HMAC-SHA256 hex du corps brut "
                    + "(secret `PAYMENT_WEBHOOK_SECRET`). Corps sandbox : "
                    + "`{internalRef, providerRef?, status, fiatAmount, "
                    + "fiatCurrency, failureReason?}`.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Issue appliquée (`CONFIRMED`, `ALREADY_PROCESSED`, "
                            + "`MARKED_FAILED`, `STILL_PENDING`)"))
    @PostMapping(value = "/{provider}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> receive(
            @PathVariable String provider,
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        PaymentWebhookService.WebhookOutcome outcome =
                webhooks.handleWebhook(provider, rawBody, signature);
        return ApiResponse.success("Webhook traité", outcome.name());
    }
}
