package com.streamingafrique.backend.payment.dto;

import java.math.BigDecimal;

import com.streamingafrique.backend.payment.model.CoinPackage;

/**
 * Pack de coins exposé à la vitrine d'achat (étape 14, §14-15).
 * Prix lus en base — le frontend ne les calcule JAMAIS.
 */
public record CoinPackageResponse(
        Long id,
        String name,
        long coinsAmount,
        BigDecimal priceAmount,
        String priceCurrency,
        boolean active,
        int sortOrder,
        String description
) {
    public static CoinPackageResponse from(CoinPackage pack) {
        return new CoinPackageResponse(pack.getId(), pack.getName(), pack.getCoinsAmount(),
                pack.getPriceAmount(), pack.getPriceCurrency(), pack.isActive(),
                pack.getSortOrder(), pack.getDescription());
    }
}
