package com.streamingafrique.backend.moderation.dto;

import com.streamingafrique.backend.moderation.model.ModerationReport;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dépôt d'un signalement (étape 17 : tout connecté peut signaler).
 * La cible est désignée par (type, id) — résolue et validée côté serveur.
 */
public record FileReportRequest(
        @NotNull(message = "Le type de cible est obligatoire")
        ModerationReport.TargetType targetType,

        @NotNull(message = "L'identifiant de la cible est obligatoire")
        Long targetId,

        @NotNull(message = "Le motif est obligatoire")
        ModerationReport.Reason reason,

        @Size(max = 2000, message = "La description ne doit pas dépasser 2000 caractères")
        String description
) {
}
