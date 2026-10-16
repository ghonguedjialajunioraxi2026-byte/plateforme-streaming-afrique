package com.streamingafrique.backend.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.streamingafrique.backend.wallet.model.WalletTransaction;

/**
 * Ligne du journal exposée (étape 14, §30) : UNE ligne par mouvement,
 * avec la référence du fait source (ex. internalRef du paiement).
 * Jamais l'entité JPA.
 */
public record WalletTransactionResponse(
        Long id,
        String direction,
        long amountCoins,
        BigDecimal fiatReferenceAmount,
        String fiatCurrency,
        long balanceBefore,
        long balanceAfter,
        String kind,
        String status,
        String reference,
        Instant createdAt
) {
    public static WalletTransactionResponse from(WalletTransaction movement) {
        return new WalletTransactionResponse(movement.getId(),
                movement.getDirection().name(), movement.getAmountCoins(),
                movement.getFiatReferenceAmount(), movement.getFiatCurrency(),
                movement.getBalanceBefore(), movement.getBalanceAfter(),
                movement.getKind().name(), movement.getStatus().name(),
                movement.getReference(), movement.getCreatedAt());
    }
}
