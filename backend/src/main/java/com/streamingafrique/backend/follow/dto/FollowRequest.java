package com.streamingafrique.backend.follow.dto;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO d'ENTRÉE — créer un suivi (stand-in de l'identité authentifiée,
 * remplacée par le jeton JWT à l'étape sécurité).
 */
public record FollowRequest(
        @Schema(description = "Abonné : doit être l'utilisateur authentifié (usurpation → 403)",
                example = "2")
        @NotNull(message = "Le followerId est obligatoire")
        Long followerId,

        @Schema(description = "Chaîne suivie", example = "1")
        @NotNull(message = "Le channelId est obligatoire")
        Long channelId
) {}