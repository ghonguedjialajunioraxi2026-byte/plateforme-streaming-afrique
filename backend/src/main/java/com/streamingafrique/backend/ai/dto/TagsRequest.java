package com.streamingafrique.backend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagsRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 2000) String description,
        @Size(max = 100) String category,
        @Size(max = 5) String language) {
}
