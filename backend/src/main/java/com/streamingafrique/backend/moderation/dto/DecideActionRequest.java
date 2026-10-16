package com.streamingafrique.backend.moderation.dto;

import java.time.Instant;

import com.streamingafrique.backend.moderation.model.ModerationAction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Décision de modération sur un signalement (étape 17).
 *
 * Effets exécutés côté serveur selon (action, cible) : avertissement ou
 * classement (aucun effet d'état), masquage de contenu, suspension de
 * compte ou de chaîne. TIMEOUT et BAN_TEMP exigent une échéance.
 */
public record DecideActionRequest(
        @NotNull(message = "L'action est obligatoire")
        ModerationAction.Action action,

        @NotNull(message = "Le périmètre est obligatoire")
        ModerationAction.Scope scope,

        /** Compte visé (requis pour TIMEOUT et bannissements). */
        Long targetUserId,

        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 500, message = "Le motif ne doit pas dépasser 500 caractères")
        String reason,

        /** Échéance (obligatoire pour TIMEOUT et BAN_TEMP, sinon permanence). */
        Instant endsAt
) {
}
