package com.streamingafrique.backend.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.payment.provider.ProviderInitiation;

/**
 * Paiement exposé (création, statut, historique — étape 14, §49).
 * Jamais l'entité JPA (contrat stable web/mobile, §62).
 */
public record PaymentResponse(
        Long id,
        String internalRef,
        String providerRef,
        String provider,
        String method,
        String status,
        BigDecimal fiatAmount,
        String fiatCurrency,
        long coinsAmount,
        String idempotencyKey,
        /** Action à présenter (redirection ou consigne) — création uniquement, NULL sinon. */
        String paymentActionType,
        String paymentActionUrl,
        String paymentInstructions,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return from(payment, null);
    }

    public static PaymentResponse from(Payment payment, ProviderInitiation initiation) {
        return new PaymentResponse(payment.getId(), payment.getInternalRef(),
                payment.getProviderRef(), payment.getProvider().name(),
                payment.getMethod().name(), payment.getStatus().name(),
                payment.getFiatAmount(), payment.getFiatCurrency(),
                payment.getCoinsCredited(), payment.getIdempotencyKey(),
                initiation == null ? null : initiation.actionType().name(),
                initiation == null ? null : initiation.actionUrl(),
                initiation == null ? null : initiation.instructions(),
                payment.getFailureReason(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
