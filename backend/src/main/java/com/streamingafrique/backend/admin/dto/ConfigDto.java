package com.streamingafrique.backend.admin.dto;

import java.time.Instant;

import com.streamingafrique.backend.admin.model.AppConfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Paramètre global versionné (étape 17 : /admin/settings). */
public record ConfigDto(
        String key,
        String value,
        int version,
        Instant effectiveFrom
) {
    public static ConfigDto from(AppConfig config) {
        return new ConfigDto(config.getConfigKey(), config.getValue(),
                config.getVersion(), config.getEffectiveFrom());
    }

    /** Écriture d'un paramètre (nouvelle version, jamais d'UPDATE). */
    public record SetConfigRequest(
            @NotBlank(message = "La clé est obligatoire")
            @Size(max = 100, message = "La clé ne doit pas dépasser 100 caractères")
            String key,

            @NotBlank(message = "La valeur est obligatoire")
            @Size(max = 2000, message = "La valeur ne doit pas dépasser 2000 caractères")
            String value
    ) {
    }
}
