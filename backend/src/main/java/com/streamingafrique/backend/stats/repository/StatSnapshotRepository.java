package com.streamingafrique.backend.stats.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.stats.model.StatSnapshot;

/**
 * Accès aux agrégats {stat_snapshots}. Courbes par périmètre/période +
 * idempotence de snapshot. AUCUNE logique métier ici.
 */
public interface StatSnapshotRepository extends JpaRepository<StatSnapshot, Long> {

    /** Courbe d'une métrique sur une plage (croissant). */
    List<StatSnapshot> findByScopeTypeAndMetricAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartAsc(
            StatSnapshot.ScopeType scopeType, StatSnapshot.Metric metric, Instant from, Instant to);

    /** Snapshots d'une chaîne sur une plage (toutes métriques). */
    List<StatSnapshot> findByScopeChainIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartAsc(
            Long chainId, Instant from, Instant to);

    /** Un snapshot précis existe-t-il déjà (garde anti-doublon du job) ? */
    Optional<StatSnapshot> findByScopeTypeAndScopeChainIdAndScopeStreamIdAndScopeVideoIdAndScopeCategoryIdAndPeriodStartAndPeriodEndAndMetric(
            StatSnapshot.ScopeType scopeType, Long chainId, Long streamId, Long videoId, Long categoryId,
            Instant periodStart, Instant periodEnd, StatSnapshot.Metric metric);
}
