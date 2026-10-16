package com.streamingafrique.backend.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Suivi de consommation IA (étape 18) → table {@code ai_usage}.
 *
 * On enregistre le COMPTEUR (provider, modèle, fonctionnalité, tokens
 * si déclarés, latence, statut) — jamais le contenu complet des prompts
 * (confidentialité + coût). Append-only.
 */
@Entity
@Table(name = "ai_usage", indexes = {
        @Index(name = "idx_ai_usage_user", columnList = "userId,createdAt"),
        @Index(name = "idx_ai_usage_feature", columnList = "feature,createdAt"),
        @Index(name = "idx_ai_usage_status", columnList = "status,createdAt")
})
public class AiUsage extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String provider = "stub";

    @Column(nullable = false, length = 80)
    private String model = "flick-stub-1";

    @Column(nullable = false)
    private Long userId;

    /** Fonctionnalité : titles, description, tags, vod-summary, analytics…​ */
    @Column(nullable = false, length = 40)
    private String feature = "assistant";

    @Column
    private Integer inputTokens;

    @Column
    private Integer outputTokens;

    @Column
    private Integer totalTokens;

    /** Latence d'appel en ms. */
    @Column
    private Long latencyMs;

    /** OK, PROVIDER_ERROR, RATE_LIMITED, QUOTA_EXCEEDED, DISABLED…​ */
    @Column(nullable = false, length = 30)
    private String status = "OK";

    /** Code d'erreur machine (null si OK). */
    @Column(length = 60)
    private String errorCode;

    protected AiUsage() {
    }

    public static AiUsage record(String provider, String model, Long userId,
                                 String feature, Integer inputTokens,
                                 Integer outputTokens, Long latencyMs,
                                 String status, String errorCode) {
        AiUsage usage = new AiUsage();
        usage.provider = provider;
        usage.model = model;
        usage.userId = userId;
        usage.feature = feature;
        usage.inputTokens = inputTokens;
        usage.outputTokens = outputTokens;
        usage.totalTokens = inputTokens == null && outputTokens == null
                ? null : (inputTokens == null ? 0 : inputTokens)
                        + (outputTokens == null ? 0 : outputTokens);
        usage.latencyMs = latencyMs;
        usage.status = status;
        usage.errorCode = errorCode;
        return usage;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFeature() {
        return feature;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
