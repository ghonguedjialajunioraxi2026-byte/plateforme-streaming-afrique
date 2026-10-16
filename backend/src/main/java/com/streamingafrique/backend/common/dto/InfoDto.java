package com.streamingafrique.backend.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Réponse de GET /api/info.
 * Informations générales et NON sensibles : aucun mot de passe,
 * aucune clé, aucun détail d'infrastructure critique.
 */
public record InfoDto(
        String application,
        String description,
        String version,
        Instant builtAt,
        String javaVersion,
        String timezone,
        List<String> activeProfiles,
        Instant startedAt,
        long uptimeSeconds
) {}