package com.streamingafrique.backend.user.dto;

import java.time.Instant;

import com.streamingafrique.backend.user.model.User;

/** DTO de SORTIE — compte utilisateur. Role sérialisé en String ("USER"...). */
public record UserDto(
        Long id,
        String email,
        String displayName,
        String avatarUrl,
        User.Role role,
        Instant createdAt
) {}