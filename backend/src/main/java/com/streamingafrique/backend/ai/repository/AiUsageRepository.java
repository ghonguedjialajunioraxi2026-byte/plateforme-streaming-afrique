package com.streamingafrique.backend.ai.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.streamingafrique.backend.ai.model.AiUsage;

/**
 * Compteurs IA : quotas (requêtes/jour/utilisateur) + monitoring admin.
 * Agrégations côté PostgreSQL, jamais en mémoire Java.
 */
@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    /** Requêtes réussies d'un utilisateur depuis {@code since} (quota). */
    long countByUserIdAndStatusAndCreatedAtAfter(Long userId, String status, Instant since);

    @Query("select u.feature as feature, count(u) as requests, "
            + "coalesce(sum(u.totalTokens), 0) as tokens, "
            + "coalesce(avg(u.latencyMs), 0) as avgLatencyMs, "
            + "sum(case when u.status = 'OK' then 0 else 1 end) as errors "
            + "from AiUsage u where u.createdAt >= :since "
            + "group by u.feature order by count(u) desc")
    List<AiUsageRow> summarizeSince(Instant since);

    /** Projection typée du récapitulatif (évite Object[] fragile). */
    interface AiUsageRow {
        String getFeature();

        long getRequests();

        long getTokens();

        double getAvgLatencyMs();

        long getErrors();
    }

    /** Dernières erreurs pour le monitoring admin (codes, sans contenu). */
    List<AiUsage> findTop50ByStatusNotOrderByCreatedAtDesc(String status);
}
