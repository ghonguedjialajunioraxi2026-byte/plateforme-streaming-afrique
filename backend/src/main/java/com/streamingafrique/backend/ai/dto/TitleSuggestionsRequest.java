package com.streamingafrique.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Demande de titres : sujet + contexte optionnel (tout est DONNÉE). */
public record TitleSuggestionsRequest(
        @NotBlank @Size(max = 500) String topic,
        @Size(max = 100) String category,
        @Size(max = 1000) String description,
        @Size(max = 100) String audience,
        @Size(max = 50) String style,
        @Size(max = 5) String language) {
}
