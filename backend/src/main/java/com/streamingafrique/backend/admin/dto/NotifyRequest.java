package com.streamingafrique.backend.admin.dto;

import java.util.List;

import com.streamingafrique.backend.notification.model.Notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Notification administrative ciblée (étape 17 : sanctions, informations).
 * Destinataires explicites (bornés à 100) — pas de diffusion aveugle.
 */
public record NotifyRequest(
        @NotEmpty(message = "Au moins un destinataire est requis")
        @Size(max = 100, message = "100 destinataires au maximum par envoi")
        List<Long> userIds,

        @NotNull(message = "Le type est obligatoire")
        Notification.Type type,

        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 150, message = "Le titre ne doit pas dépasser 150 caractères")
        String title,

        @NotBlank(message = "Le message est obligatoire")
        @Size(max = 500, message = "Le message ne doit pas dépasser 500 caractères")
        String message,

        @Size(max = 500, message = "Le lien ne doit pas dépasser 500 caractères")
        String linkUrl
) {
}
