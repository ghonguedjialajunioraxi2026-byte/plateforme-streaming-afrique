package com.streamingafrique.backend.ai.dto;

import java.util.List;

/** Réponse ancrée sur la base de connaissances officielle. */
public record AssistantResponse(String answer, List<String> sources, String provider, String model) {
}
