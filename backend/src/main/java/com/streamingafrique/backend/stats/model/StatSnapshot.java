package com.streamingafrique.backend.stats.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.streamingafrique.backend.category.model.Category;
import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.video.model.Video;

/**
 * Entité "StatSnapshot" → table {@code stat_snapshots}.
 *
 * RÔLE : les AGRÉGATS statistiques immuables (sous-étape 7.23) — une ligne =
 * une métrique, un périmètre, une période. On ne réécrit JAMAIS le passé :
 * une correction = nouvelle ligne (même périmètre/période/métrique refusée
 * par l'UNIQUE, donc via une période de rattrapage ou une version métier).
 *
 * Anti-doublon : l'UNIQUE JPA ne couvre pas les périmètres NULL (en SQL,
 * NULL est distinct de NULL — une contrainte multi-colonnes à NULL ne
 * bloque rien). La garde est donc le finder
 * {@code findByScopeTypeAnd...AndMetric} (job idempotent : présent → on
 * saute) + de FUTURS index partiels DDL par scope
 * (ex. UNIQUE(chain_id, period, metric) WHERE scope_type='CHAIN').
 *
 * Séparation (exigée) :
 *   - OPÉRATIONNEL (temps réel, hors base) : viewers instantanés en Redis,
 *     pic courant sur {@code streams.peak_viewers}, compteurs directs via
 *     repositories — jamais stocké ici à chaque pulsation ;
 *   - HISTORIQUE brut (événements fins) : tables sources (follows,
 *     gift_sends, subscriptions, watch_history à venir) — purgé/agrégé
 *     après fenêtre, jamais requêté en ligne pour des courbes ;
 *   - AGRÉGATS (cette table) : snapshots HOUR/DAY/WEEK/MONTH — seule source
 *     des courbes créateur/plateforme. Les jobs futurs roulent DAY depuis
 *     HOUR, WEEK/MONTH depuis DAY (rollups, pas de recalcul sur le brut).
 *
 * Les tables principales ne grossissent pas : aucune colonne de stats
 * ajoutée à streams/videos/creator_profiles (seul peak_viewers, déjà là
 * en 7.13, y figure — compteur monotone, pas une série).
 */
@Entity
@Table(name = "stat_snapshots",
        uniqueConstraints = {
                // Noms PHYSIQUES (les FK portent leur @JoinColumn : scope_*_id).
                @UniqueConstraint(name = "uk_stats_scope_period_metric",
                        columnNames = {"scope_type", "scope_chain_id", "scope_stream_id", "scope_video_id",
                                "scope_category_id", "period_start", "period_end", "metric"})
        },
        indexes = {
                @Index(name = "idx_stats_scope_period", columnList = "scopeType,periodStart")
        })
public class StatSnapshot extends BaseEntity {

    /** Périmètre mesuré (PLATFORM = tous les scope_* NULL). */
    public enum ScopeType {
        PLATFORM, CHAIN, STREAM, VIDEO, CATEGORY
    }

    /** Grain de la période (rollups HOUR → DAY → WEEK → MONTH). */
    public enum Granularity {
        HOUR, DAY, WEEK, MONTH
    }

    /** Métrique (une ligne par métrique : pas de colonnes qui gonflent). */
    public enum Metric {
        PEAK_VIEWERS, VIEWS, WATCH_SECONDS, FOLLOWS_GAINED, SUBSCRIPTIONS,
        REVENUE_COINS, GIFTS_COUNT
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScopeType scopeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_chain_id")
    private CreatorProfile scopeChain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_stream_id")
    private Stream scopeStream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_video_id")
    private Video scopeVideo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_category_id")
    private Category scopeCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Granularity granularity;

    /** Période [start, end) — fin strictement après début. */
    @Column(nullable = false)
    private Instant periodStart;

    @Column(nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Metric metric;

    /**
     * Valeur agrégée (>= 0). Colonne physique {@code metric_value} :
     * {@code value} est un mot réservé SQL (H2/PostgreSQL).
     */
    @Column(name = "metric_value", nullable = false)
    private long value;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected StatSnapshot() {
    }

    /** Fabrique : périmètre cohérent (PLATFORM = scopes NULL), période et valeur valides. */
    public static StatSnapshot record(ScopeType scopeType, CreatorProfile scopeChain, Stream scopeStream,
                                      Video scopeVideo, Category scopeCategory, Granularity granularity,
                                      Instant periodStart, Instant periodEnd, Metric metric, long value) {
        if (scopeType == null || granularity == null || metric == null) {
            throw new IllegalArgumentException("Périmètre, grain et métrique sont obligatoires");
        }
        if (periodStart == null || periodEnd == null || !periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("La fin de période doit être après son début");
        }
        if (value < 0) {
            throw new IllegalArgumentException("La valeur agrégée ne peut pas être négative");
        }
        boolean coherent = switch (scopeType) {
            case PLATFORM -> scopeChain == null && scopeStream == null && scopeVideo == null && scopeCategory == null;
            case CHAIN -> scopeChain != null && scopeStream == null && scopeVideo == null && scopeCategory == null;
            case STREAM -> scopeStream != null && scopeChain == null && scopeVideo == null && scopeCategory == null;
            case VIDEO -> scopeVideo != null && scopeChain == null && scopeStream == null && scopeCategory == null;
            case CATEGORY -> scopeCategory != null && scopeChain == null && scopeStream == null && scopeVideo == null;
        };
        if (!coherent) {
            throw new IllegalArgumentException("Périmètre incohérent : exactement le champ du scope renseigné");
        }
        StatSnapshot snapshot = new StatSnapshot();
        snapshot.scopeType = scopeType;
        snapshot.scopeChain = scopeChain;
        snapshot.scopeStream = scopeStream;
        snapshot.scopeVideo = scopeVideo;
        snapshot.scopeCategory = scopeCategory;
        snapshot.granularity = granularity;
        snapshot.periodStart = periodStart;
        snapshot.periodEnd = periodEnd;
        snapshot.metric = metric;
        snapshot.value = value;
        return snapshot;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public CreatorProfile getScopeChain() {
        return scopeChain;
    }

    public Stream getScopeStream() {
        return scopeStream;
    }

    public Video getScopeVideo() {
        return scopeVideo;
    }

    public Category getScopeCategory() {
        return scopeCategory;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Metric getMetric() {
        return metric;
    }

    public long getValue() {
        return value;
    }
}
