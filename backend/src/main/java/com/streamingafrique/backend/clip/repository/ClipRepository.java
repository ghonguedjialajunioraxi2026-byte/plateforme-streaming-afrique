package com.streamingafrique.backend.clip.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.video.model.Video;

/**
 * Accès aux clips {clips}.
 *
 * Méthodes JUSTE nécessaires : les clips d'une chaîne, d'un direct,
 * d'une vidéo, ou par visibilité. TRI et PAGINATION via Pageable.
 * (Un clip a UNE SEULE source — stream OU video — la contrainte @Check
 * du modèle le garantit.)
 *
 * {@link JpaSpecificationExecutor} : recherche/filtres combinés en base.
 */
public interface ClipRepository extends JpaRepository<Clip, Long>, JpaSpecificationExecutor<Clip> {

    /** Les clips d'un créateur, paginés. */
    Page<Clip> findByCreatorId(Long creatorId, Pageable pageable);

    /** Les clips tirés d'un direct, paginés. */
    Page<Clip> findByStreamId(Long streamId, Pageable pageable);

    /** Les clips tirés d'une vidéo, paginés. */
    Page<Clip> findByVideoId(Long videoId, Pageable pageable);

    /** Les clips par visibilité (feed public : PUBLIC), paginés. */
    Page<Clip> findByVisibility(Clip.Visibility visibility, Pageable pageable);

    /**
     * Incrément ATOMIQUE du compteur (étape 15, comme les VOD).
     */
    @Modifying
    @Query("update Clip c set c.viewCount = c.viewCount + 1 where c.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** Clips issus d'une vidéo (politique de suppression source, §49). */
    List<Clip> findByVideo(Video video);

    /**
     * Résolution du lien court de partage (étape 15, §40-41 : {@code /c/{shareSlug}}).
     * Unicité garantie en base (uk_clips_share_slug).
     */
    Optional<Clip> findByShareSlug(String shareSlug);

    /** Traitements bloqués (rejeu au boot : crash/redéploiement, §36). */
    List<Clip> findByProcessingStatus(Clip.ProcessingStatus status);

    /** Nombre de clips d'une chaîne (dashboard créateur). */
    long countByCreatorId(Long creatorId);

    /** Vues cumulées des clips d'une chaîne (dashboard créateur). */
    @Query("select coalesce(sum(c.viewCount), 0) from Clip c where c.creator.id = :creatorId")
    long sumViewCountByCreatorId(@Param("creatorId") Long creatorId);

    /** Top clips d'une chaîne (dashboard créateur : contenus performants). */
    List<Clip> findTop5ByCreatorIdOrderByViewCountDesc(Long creatorId);
}