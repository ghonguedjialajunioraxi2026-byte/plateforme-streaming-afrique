package com.streamingafrique.backend.stream.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Réponse d'une pulsation : audience instantanée (approximative, Redis HLL). */
public record ViewerHeartbeatResponse(
        @Schema(description = "Direct concerné", example = "12")
        Long streamId,

        @Schema(description = "Spectateurs estimés en ce moment", example = "120")
        long viewerCount
) {}
