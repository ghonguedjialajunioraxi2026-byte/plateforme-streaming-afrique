package com.streamingafrique.backend.region.dto;

import java.util.List;
import java.util.Map;

/**
 * Configuration régionale publique (cachable) : feature flags
 * {@code features.*}, statut des taux de change (aucun taux inventé :
 * {@code exchangeRatesAvailable=false} tant qu'aucune source fiable
 * n'est branchée).
 */
public record RegionConfigDto(
        Map<String, Boolean> features,
        boolean exchangeRatesAvailable,
        List<String> activeCountries) {
}
