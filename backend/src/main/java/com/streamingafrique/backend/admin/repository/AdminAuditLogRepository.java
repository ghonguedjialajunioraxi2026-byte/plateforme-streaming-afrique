package com.streamingafrique.backend.admin.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.admin.model.AdminAuditLog;

/**
 * Accès au journal d'audit {admin_audit_log}.
 * Actes d'un admin + dossier d'une ressource. Lecture seule par nature.
 * AUCUNE logique métier ici.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /** Actes d'un administrateur, récents d'abord. */
    List<AdminAuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId);

    /** Dossier d'une ressource (tout ce qui lui est arrivé). */
    List<AdminAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    /**
     * Journal paginé avec filtres optionnels (étape 17 : /admin/logs —
     * action et/ou acteur, récents d'abord).
     */
    @Query("""
            select a from AdminAuditLog a
             where (:action is null or a.action = :action)
               and (:actorId is null or a.actor.id = :actorId)
            """)
    Page<AdminAuditLog> search(@Param("action") String action,
                               @Param("actorId") Long actorId,
                               Pageable pageable);
}
