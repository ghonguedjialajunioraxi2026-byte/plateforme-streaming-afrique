package com.streamingafrique.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Message à classifier (modération assistée, MODERATOR+). */
public record ClassifyRequest(
        @NotBlank @Size(max = 2000) String text,
        @Size(max = 5) String language) {
}
