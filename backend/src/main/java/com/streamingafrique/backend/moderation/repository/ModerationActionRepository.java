package com.streamingafrique.backend.moderation.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.moderation.model.ModerationAction;

/**
 * Accès à l'historique des décisions {moderation_actions}.
 * Dossier d'un compte + sanctions actives + actes d'un modérateur.
 * AUCUNE logique métier ici.
 */
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    /** Dossier complet d'un compte sanctionné (toutes, y compris levées). */
    List<ModerationAction> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);

    /** Sanctions en cours d'un compte (blocage à l'accès). */
    List<ModerationAction> findByTargetUserIdAndState(Long targetUserId, ModerationAction.State state);

    /** Actes d'un modérateur (audit interne). */
    List<ModerationAction> findByModeratorId(Long moderatorId);

    /** Historique paginé par état (modération : sanctions actives, levées…). */
    Page<ModerationAction> findByStateOrderByCreatedAtDesc(ModerationAction.State state,
                                                          Pageable pageable);

    /** Historique paginé complet, récent d'abord. */
    Page<ModerationAction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
