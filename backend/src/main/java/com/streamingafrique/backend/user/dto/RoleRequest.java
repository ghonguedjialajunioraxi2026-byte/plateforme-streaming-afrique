package com.streamingafrique.backend.user.dto;

import com.streamingafrique.backend.user.model.User;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** DTO d'ENTRÉE — changement de rôle d'un compte. */
public record RoleRequest(
        @Schema(description = "Nouveau rôle (hiérarchie USER < CREATOR < MODERATOR < ADMIN)",
                example = "CREATOR")
        @NotNull(message = "Le rôle est obligatoire")
        User.Role role
) {}