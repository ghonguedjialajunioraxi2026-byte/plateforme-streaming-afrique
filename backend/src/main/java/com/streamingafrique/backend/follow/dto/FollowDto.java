package com.streamingafrique.backend.follow.dto;

import java.time.Instant;

/** DTO de SORTIE — un suivi (relation spectateur → chaîne). */
public record FollowDto(
        Long id,
        Long followerId,
        Long followedId,
        Instant createdAt
) {}