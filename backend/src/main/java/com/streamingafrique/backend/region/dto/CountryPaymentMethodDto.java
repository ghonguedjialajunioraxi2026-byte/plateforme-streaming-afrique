package com.streamingafrique.backend.region.dto;

import com.streamingafrique.backend.region.model.CountryPaymentMethod;

/**
 * Moyen de paiement d'un pays. {@code active=false} = préparé, pas
 * encore intégré (aucun paiement réel ne part vers ce moyen).
 */
public record CountryPaymentMethodDto(
        Long id,
        String countryCode,
        String provider,
        String method,
        String currency,
        boolean active,
        boolean sandbox) {

    public static CountryPaymentMethodDto from(CountryPaymentMethod row,
                                               boolean sandboxMode) {
        return new CountryPaymentMethodDto(row.getId(), row.getCountryCode(),
                row.getProvider(), row.getMethod(), row.getCurrency(),
                row.isActive(), sandboxMode);
    }
}
