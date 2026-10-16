package com.streamingafrique.backend.creator.dashboard.dto;

import java.util.List;

import com.streamingafrique.backend.wallet.dto.WalletResponse;
import com.streamingafrique.backend.wallet.dto.WalletTransactionResponse;

/**
 * Revenus de la chaîne (étape 16 : {@code GET /api/creator/revenue}).
 *
 * Monnaie virtuelle (coins) et argent réel (références fiat des lignes) ne
 * sont JAMAIS mélangés : les totaux sont en coins, chaque ligne garde sa
 * référence fiat d'origine. Le backend (journal {@code transactions}) reste
 * la seule source de vérité — aucun calcul critique côté frontend.
 */
public record CreatorRevenueDto(
        WalletResponse wallet,
        long giftCoinsReceived,
        long subscriptionCoinsReceived,
        long totalCoinsReceived,
        long giftsReceived,
        List<WalletTransactionResponse> recentTransactions
) {
}
