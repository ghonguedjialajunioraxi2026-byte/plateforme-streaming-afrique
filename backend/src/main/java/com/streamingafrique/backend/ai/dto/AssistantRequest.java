package com.streamingafrique.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Question à l'assistant Flick (utilisateur connecté). */
public record AssistantRequest(
        @NotBlank @Size(max = 1000) String question,
        @Size(max = 5) String language,
        /** Pays déclaré (contexte régional, DONNÉE — ex. "CM"). */
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Pays invalide (ISO alpha-2)")
        String country) {

    public AssistantRequest(String question, String language) {
        this(question, language, null);
    }
}
