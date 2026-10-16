package com.streamingafrique.backend.ai.dto;

/**
 * Classification IA = RECOMMANDATION pour un modérateur humain.
 * L'IA ne sanctionne JAMAIS seule (ni ban, ni timeout automatique).
 */
public record ClassificationResponse(
        String verdict,
        double confidence,
        String category,
        String reason,
        boolean humanReviewRequired,
        String provider,
        String model) {
}
