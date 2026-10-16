package com.streamingafrique.backend.ai.dto;

import java.util.List;

/**
 * Suggestions de titres — PROPOSITIONS à valider par le créateur.
 * L'IA ne modifie JAMAIS la chaîne directement.
 */
public record TitleSuggestionsResponse(List<String> suggestions, String provider, String model) {
}
