package com.streamingafrique.backend.video.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.video.model.Video;

/**
 * Accès aux vidéos VOD {videos}.
 *
 * Méthodes JUSTE nécessaires : les vidéos d'une chaîne, par catégorie,
 * ou par visibilité (feed public = PUBLIC uniquement). TRI et PAGINATION
 * via l'objet Pageable fourni par l'appelant.
 *
 * {@link JpaSpecificationExecutor} : recherche/filtres combinés
 * (search + créateur + catégorie + visibilité) exécutés en base.
 */
public interface VideoRepository extends JpaRepository<Video, Long>, JpaSpecificationExecutor<Video> {

    /** Les vidéos d'un créateur, paginées. */
    Page<Video> findByCreatorId(Long creatorId, Pageable pageable);

    /** Les vidéos d'une catégorie, paginées. */
    Page<Video> findByCategoryId(Long categoryId, Pageable pageable);

    /** Les vidéos par visibilité (feed public : PUBLIC), paginées. */
    Page<Video> findByVisibility(Video.Visibility visibility, Pageable pageable);

    /**
     * Incrément ATOMIQUE du compteur (étape 15) : une seule requête UPDATE,
     * jamais de lecture-modification concurrente (pas de vue perdue).
     */
    @Modifying
    @Query("update Video v set v.viewCount = v.viewCount + 1 where v.id = :id")
    void incrementViewCount(@Param("id") Long id);

    /** Traitements bloqués (rejeu au boot : crash/redéploiement, §14). */
    List<Video> findByProcessingStatus(Video.ProcessingStatus status);

    /**
     * Nombre de VOD d'une chaîne (dashboard créateur — agrégation SQL, jamais
     * de chargement mémoire).
     */
    long countByCreatorId(Long creatorId);

    /**
     * Vues cumulées des VOD d'une chaîne (dashboard créateur).
     */
    @Query("select coalesce(sum(v.viewCount), 0) from Video v where v.creator.id = :creatorId")
    long sumViewCountByCreatorId(@Param("creatorId") Long creatorId);

    /** Top VOD d'une chaîne (dashboard créateur : contenus performants). */
    List<Video> findTop5ByCreatorIdOrderByViewCountDesc(Long creatorId);
}