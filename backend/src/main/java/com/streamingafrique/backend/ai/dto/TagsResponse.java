package com.streamingafrique.backend.ai.dto;

import java.util.List;

public record TagsResponse(List<String> tags, String provider, String model) {
}
