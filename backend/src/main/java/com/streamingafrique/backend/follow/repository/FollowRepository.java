package com.streamingafrique.backend.follow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.follow.model.Follow;

/**
 * Accès aux suivis {follows}.
 *
 * Méthodes JUSTE nécessaires : "est-ce que je suis déjà ?", compteurs
 * (abonnés d'une chaîne / chaînes suivies) et la liste PAGINÉE des
 * chaînes suivies par un utilisateur (requête JPQL = simple lecture,
 * sans logique métier).
 */
public interface FollowRepository extends JpaRepository<Follow, Long> {

    /** Vrai si l'utilisateur suit déjà cette chaîne (anti-doublon). */
    boolean existsByFollowerIdAndFollowedId(Long followerId, Long followedId);

    /** Le suivi précis — marginal (ne plus suivre, idempotence). */
    Optional<Follow> findByFollowerIdAndFollowedId(Long followerId, Long followedId);

    /** Nombre de followers d'une chaîne. */
    long countByFollowedId(Long followedId);

    /** Nombre de chaînes suivies par un utilisateur. */
    long countByFollowerId(Long followerId);

    /** Les fiches des chaînes suivies par un utilisateur, paginées ("@/suivre"). */
    @Query("""
            select f.followed
              from Follow f
             where f.follower.id = :followerId
            """)
    Page<CreatorProfile> findFollowedProfilesByFollowerId(@Param("followerId") Long followerId, Pageable pageable);

    /**
     * Les suivis reçus par une chaîne, récents d'abord (dashboard créateur :
     * communauté — « nouveaux followers »).
     */
    Page<Follow> findByFollowedIdOrderByCreatedAtDesc(Long followedId, Pageable pageable);

    /**
     * Croissance par chaîne depuis {@code since} (étape 19 : créateurs
     * émergents) — [chaîne id, follows récents], ordre décroissant.
     * Agrégation SQL, jamais en mémoire.
     */
    @Query("""
            select f.followed.id, count(f.id)
              from Follow f
             where f.createdAt >= :since
             group by f.followed.id
             order by count(f.id) desc
            """)
    List<Object[]> countRecentFollowsByChannel(
            @Param("since") java.time.Instant since);
}