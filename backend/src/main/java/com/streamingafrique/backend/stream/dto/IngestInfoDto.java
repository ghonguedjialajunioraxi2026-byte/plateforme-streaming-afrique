package com.streamingafrique.backend.stream.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Informations d'ingestion d'un direct pour SON propriétaire (étape 10).
 *
 * URLs + présence d'une clé (jamais la clé elle-même : elle n'est visible
 * qu'à la génération/rotation). Page « aide OBS » du créateur.
 */
public record IngestInfoDto(
        @Schema(description = "Direct concerné", example = "12")
        Long streamId,

        @Schema(description = "URL RTMP (champ « serveur » d'OBS)", example = "rtmp://localhost:1935/live")
        String rtmpUrl,

        @Schema(description = "URL HLS de relecture/contrôle", example = "http://localhost:18080/live/12.m3u8")
        String playbackUrl,

        @Schema(description = "Protocole d'ingest attendu", example = "RTMP")
        String ingestProtocol,

        @Schema(description = "Une clé active existe-t-elle ? (régénérer si perdue)", example = "true")
        boolean hasKey
) {}
