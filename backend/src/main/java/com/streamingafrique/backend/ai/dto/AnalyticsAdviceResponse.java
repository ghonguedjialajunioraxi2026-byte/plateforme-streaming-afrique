package com.streamingafrique.backend.ai.dto;

import java.util.List;

/**
 * Conseil analytics — chiffres RÉELS fournis par le service.
 * Si les données sont insuffisantes, {@code dataSufficient=false}
 * et l'IA le signale au lieu d'inventer.
 */
public record AnalyticsAdviceResponse(
        String analysis,
        List<String> suggestions,
        boolean dataSufficient,
        String provider,
        String model) {
}
