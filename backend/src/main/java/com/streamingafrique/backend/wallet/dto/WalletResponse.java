package com.streamingafrique.backend.wallet.dto;

import com.streamingafrique.backend.wallet.model.Wallet;

/**
 * Portefeuille exposé (étape 14) : solde de coins lisible.
 * Jamais l'entité JPA.
 */
public record WalletResponse(
        long balanceCoins,
        long reservedCoins,
        long availableCoins,
        String status
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getBalanceCoins(), wallet.getReservedCoins(),
                wallet.getBalanceCoins() - wallet.getReservedCoins(),
                wallet.getStatus().name());
    }
}
