package com.streamingafrique.backend.ai.dto;

import java.util.List;

/**
 * Intention extraite → le frontend réutilise le MOTEUR DE
 * RECHERCHE EXISTANT (jamais remplacé par un LLM).
 */
public record SearchAssistResponse(
        List<String> keywords,
        String category,
        String region,
        String searchQuery,
        String provider,
        String model) {
}
