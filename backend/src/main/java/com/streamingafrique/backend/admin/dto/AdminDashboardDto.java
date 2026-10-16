package com.streamingafrique.backend.admin.dto;

import java.util.Map;

/**
 * Vue d'ensemble plateforme (étape 17 : {@code GET /api/admin/dashboard}).
 *
 * Que des compteurs SQL (jamais de chargement mémoire). Les paiements sont
 * ventilés par statut (montants multi-devises : aucun total monétaire
 * global, jamais de mélange de devises).
 */
public record AdminDashboardDto(
        long usersTotal,
        long usersActive,
        long usersSuspended,
        long creatorsTotal,
        long liveStreams,
        long videosTotal,
        long clipsTotal,
        long followersTotal,
        long subscriptionsActive,
        Map<String, Long> paymentsByStatus,
        long openReports,
        long auditEntries
) {
}
