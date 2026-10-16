package com.streamingafrique.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requête en langage naturel (« du gaming camerounais »…). */
public record SearchAssistRequest(
        @NotBlank @Size(max = 300) String query,
        @Size(max = 5) String language,
        /** Pays déclaré (contexte régional, DONNÉE — ex. "NG"). */
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "Pays invalide (ISO alpha-2)")
        String country) {

    public SearchAssistRequest(String query, String language) {
        this(query, language, null);
    }
}
