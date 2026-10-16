package com.streamingafrique.backend.stats;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.stats.model.StatSnapshot;
import com.streamingafrique.backend.stats.repository.StatSnapshotRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.23 : agrégats immuables par périmètre/période/métrique,
 * courbes et anti-doublon (H2, transaction annulée). L'opérationnel
 * (Redis, compteurs directs) n'est pas stocké ici.
 */
@SpringBootTest
@Transactional
class StatsStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StatSnapshotRepository snapshots;

    @Test
    void snapshot_chaine_journalier_et_courbe() {
        User owner = users.save(User.create("stats@example.com", "stats", "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(owner, "Stats TV", "stats-tv"));
        Instant jour = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant lendemain = jour.plus(1, ChronoUnit.DAYS);

        snapshots.save(StatSnapshot.record(StatSnapshot.ScopeType.CHAIN, channel, null, null, null,
                StatSnapshot.Granularity.DAY, jour, lendemain, StatSnapshot.Metric.FOLLOWS_GAINED, 12));
        snapshots.save(StatSnapshot.record(StatSnapshot.ScopeType.CHAIN, channel, null, null, null,
                StatSnapshot.Granularity.DAY, jour, lendemain, StatSnapshot.Metric.REVENUE_COINS, 450));
        snapshots.saveAndFlush(StatSnapshot.record(StatSnapshot.ScopeType.PLATFORM, null, null, null, null,
                StatSnapshot.Granularity.DAY, jour, lendemain, StatSnapshot.Metric.PEAK_VIEWERS, 900));

        var courbe = snapshots.findByScopeChainIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartAsc(
                channel.getId(), jour, lendemain.plusSeconds(1));
        assertEquals(2, courbe.size());

        var plateforme = snapshots
                .findByScopeTypeAndMetricAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqualOrderByPeriodStartAsc(
                        StatSnapshot.ScopeType.PLATFORM, StatSnapshot.Metric.PEAK_VIEWERS, jour,
                        lendemain.plusSeconds(1));
        assertEquals(1, plateforme.size());
    }

    @Test
    void doublon_detecte_par_garde_avant_ecriture() {
        // L'UNIQUE multi-colonnes à NULL ne bloque rien en SQL (NULL distinct
        // de NULL) : la garde anti-doublon du job est le finder, adossé plus
        // tard à des index partiels DDL par scope (voir Javadoc entité).
        User owner = users.save(User.create("statsbis@example.com", "statsbis", "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(owner, "StatsBis TV", "statsbis-tv"));
        Instant jour = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant lendemain = jour.plus(1, ChronoUnit.DAYS);

        snapshots.saveAndFlush(StatSnapshot.record(StatSnapshot.ScopeType.CHAIN, channel, null, null, null,
                StatSnapshot.Granularity.DAY, jour, lendemain, StatSnapshot.Metric.VIEWS, 100));
        assertTrue(snapshots
                .findByScopeTypeAndScopeChainIdAndScopeStreamIdAndScopeVideoIdAndScopeCategoryIdAndPeriodStartAndPeriodEndAndMetric(
                        StatSnapshot.ScopeType.CHAIN, channel.getId(), null, null, null,
                        jour, lendemain, StatSnapshot.Metric.VIEWS)
                .isPresent(), "la garde doit détecter le snapshot existant avant réécriture");
    }

    @Test
    void perimetre_incoherent_periode_inversee_valeur_negative_refuses() {
        User owner = users.save(User.create("statster@example.com", "statster", "hash"));
        CreatorProfile channel = channels.save(CreatorProfile.create(owner, "Ter TV", "ter-tv"));
        Instant jour = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant lendemain = jour.plus(1, ChronoUnit.DAYS);

        assertThrows(IllegalArgumentException.class, () -> StatSnapshot.record(
                StatSnapshot.ScopeType.PLATFORM, channel, null, null, null,
                StatSnapshot.Granularity.DAY, jour, lendemain, StatSnapshot.Metric.VIEWS, 1));
        assertThrows(IllegalArgumentException.class, () -> StatSnapshot.record(
                StatSnapshot.ScopeType.CHAIN, channel, null, null, null,
                StatSnapshot.Granularity.DAY, lendemain, jour, StatSnapshot.Metric.VIEWS, 1));
        assertThrows(IllegalArgumentException.class, () -> StatSnapshot.record(
                StatSnapshot.ScopeType.CHAIN, channel, null, null, null,
                StatSnapshot.Granularity.DAY, jour, lendemain, StatSnapshot.Metric.VIEWS, -1));
        assertTrue(true);
    }
}
