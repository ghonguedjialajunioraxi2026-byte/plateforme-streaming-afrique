package com.streamingafrique.backend.stream.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Réponse de génération/rotation de stream key (étape 10, PARTIE 3).
 *
 * Le SEUL endroit où la clé en clair transite : elle est montrée UNE fois
 * au propriétaire (génération/rotation) puis OUBLIÉE (seule l'empreinte
 * SHA-256 persiste). Jamais loggée, jamais ré-exposable.
 */
public record StreamKeyResponse(
        @Schema(description = "Direct concerné", example = "12")
        Long streamId,

        @Schema(description = "URL RTMP à saisir dans OBS (serveur seul, sans secret)",
                example = "rtmp://localhost:1935/live")
        String rtmpUrl,

        @Schema(description = "Clé à saisir dans OBS (MONTRÉE UNE SEULE FOIS — la copier maintenant)",
                example = "sa_9f2c…")
        String streamKey,

        @Schema(description = "Format complet accepté par OBS dans le champ « clé »",
                example = "12?key=sa_9f2c…")
        String obsStreamKey
) {}
