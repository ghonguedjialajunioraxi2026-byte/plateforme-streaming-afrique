package com.streamingafrique.backend.ai.dto;

import java.util.List;

/**
 * Résumé de VOD honnête : construit depuis les MÉTADONNÉES
 * (titre, description, durée, vues). Si aucune transcription
 * n'existe, {@code transcriptAvailable=false} et aucun chapitre
 * temporel inventé.
 */
public record VodSummaryResponse(
        String summary,
        String improvedDescription,
        List<String> keyPoints,
        boolean transcriptAvailable,
        String provider,
        String model) {
}
