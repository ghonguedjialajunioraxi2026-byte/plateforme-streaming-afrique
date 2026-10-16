package com.streamingafrique.backend.moderation.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.moderation.model.ModerationReport;

/**
 * Accès aux signalements {moderation_reports}.
 * File des modérateurs par statut + dossier d'un reporter.
 * AUCUNE logique métier ici.
 */
public interface ModerationReportRepository extends JpaRepository<ModerationReport, Long> {

    /** File de traitement par état (les OPEN d'abord). */
    List<ModerationReport> findByStatusOrderByCreatedAtAsc(ModerationReport.Status status);

    /** File paginée (modération : /moderator/reports). */
    Page<ModerationReport> findByStatusOrderByCreatedAtDesc(ModerationReport.Status status,
                                                           Pageable pageable);

    /** Signalements émis par un utilisateur (détection d'abus). */
    List<ModerationReport> findByReporterId(Long reporterId);

    /** Volume par état (pilotage). */
    long countByStatus(ModerationReport.Status status);

    /**
     * Signalements visant directement une chaîne (dashboard créateur :
     * modération de son espace).
     */
    List<ModerationReport> findByTargetChainIdAndStatusOrderByCreatedAtDesc(
            Long targetChainId, ModerationReport.Status status);
}
