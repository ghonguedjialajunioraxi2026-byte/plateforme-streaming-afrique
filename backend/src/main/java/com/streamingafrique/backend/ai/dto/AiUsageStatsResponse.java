package com.streamingafrique.backend.ai.dto;

import java.util.List;

/** Monitoring admin : compteurs par fonctionnalité (ADMIN). */
public record AiUsageStatsResponse(
        String provider,
        String model,
        boolean enabled,
        List<FeatureUsage> features,
        List<AiErrorEntry> recentErrors) {

    public record FeatureUsage(
            String feature,
            long requests,
            long tokens,
            double avgLatencyMs,
            long errors) {
    }

    public record AiErrorEntry(
            Long id, String feature, String status, String errorCode, String createdAt) {
    }
}
