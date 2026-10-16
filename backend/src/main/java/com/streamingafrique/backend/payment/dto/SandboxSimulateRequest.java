package com.streamingafrique.backend.payment.dto;

import com.streamingafrique.backend.payment.provider.WebhookVerification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Simulation sandbox d'une issue de paiement (dev/test — le simulateur est
 * inactif en production). Rejoue le pipeline webhook RÉEL (signé en interne).
 */
public record SandboxSimulateRequest(
        @NotBlank(message = "La référence interne est obligatoire")
        String internalRef,

        @NotNull(message = "L'issue simulée est obligatoire (SUCCEEDED, FAILED ou PENDING)")
        WebhookVerification.Outcome outcome
) {
}
