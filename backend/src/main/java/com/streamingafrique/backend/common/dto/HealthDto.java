package com.streamingafrique.backend.common.dto;

/**
 * Réponse de GET /api/health.
 * Record : syntaxe compacte, Jackson le sérialise en JSON tout seul.
 */
public record HealthDto(String status, String application) {

    /**
     * Fabrique pratique : renvoie un état UP (l'application répond).
     */
    public static HealthDto up(String application) {
        return new HealthDto("UP", application);
    }
}