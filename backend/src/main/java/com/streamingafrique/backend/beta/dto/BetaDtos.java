package com.streamingafrique.backend.beta.dto;

import java.time.Instant;

import com.streamingafrique.backend.beta.model.BetaFeedback;
import com.streamingafrique.backend.beta.model.BetaInvitation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTO bêta privée (étape 24) — jamais d'entité exposée, jamais de code en clair en lecture. */
public final class BetaDtos {

    private BetaDtos() {
    }

    public record CreateInvitationRequest(
            @NotBlank(message = "L'email invité est obligatoire")
            @Email(message = "L'email invité doit être valide")
            @Size(max = 150) String email,
            @Size(max = 280, message = "La note ne doit pas dépasser 280 caractères") String note) {
    }

    /** Réponse de création : le code en clair n'apparaît QU'ICI (montré une fois à l'admin). */
    public record InvitationCreatedResponse(
            Long id, String email, String code, Instant expiresAt) {
    }

    public record BetaInvitationDto(
            Long id, String email, BetaInvitation.Status status,
            Instant expiresAt, Instant acceptedAt, Long acceptedUserId, Instant createdAt) {
        public static BetaInvitationDto of(BetaInvitation invitation) {
            return new BetaInvitationDto(invitation.getId(), invitation.getEmail(),
                    invitation.getStatus(), invitation.getExpiresAt(),
                    invitation.getAcceptedAt(), invitation.getAcceptedUserId(),
                    invitation.getCreatedAt());
        }
    }

    public record SubmitFeedbackRequest(
            @NotNull(message = "La catégorie est obligatoire") BetaFeedback.Category category,
            @NotBlank(message = "Le titre est obligatoire")
            @Size(max = 140, message = "Le titre ne doit pas dépasser 140 caractères") String title,
            @NotBlank(message = "La description est obligatoire")
            @Size(max = 4000, message = "La description ne doit pas dépasser 4000 caractères")
            String description,
            @Size(max = 200) String route,
            @Size(max = 40) String appVersion,
            @Size(max = 120) String device) {
    }

    public record TriageFeedbackRequest(
            @NotNull(message = "Le statut est obligatoire") BetaFeedback.Status status,
            BetaFeedback.Priority priority) {
    }

    public record BetaFeedbackDto(
            Long id, Long reporterId, BetaFeedback.Category category,
            String title, String description, String route, String appVersion, String device,
            BetaFeedback.Status status, BetaFeedback.Priority priority, Instant createdAt) {
        public static BetaFeedbackDto of(BetaFeedback feedback) {
            return new BetaFeedbackDto(feedback.getId(), feedback.getReporter().getId(),
                    feedback.getCategory(), feedback.getTitle(), feedback.getDescription(),
                    feedback.getRoute(), feedback.getAppVersion(), feedback.getDevice(),
                    feedback.getStatus(), feedback.getPriority(), feedback.getCreatedAt());
        }
    }
}
