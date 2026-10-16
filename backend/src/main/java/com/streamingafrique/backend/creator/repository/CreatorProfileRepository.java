package com.streamingafrique.backend.creator.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.creator.model.CreatorProfile;

/**
 * Accès aux fiches créateurs {creator_profiles}.
 *
 * Méthodes JUSTE nécessaires (URL publique par slug, fiche par compte,
 * unicité du slug à la création de chaîne). AUCUNE logique métier ici.
 *
 * {@link JpaSpecificationExecutor} : recherche/filtre dynamiques
 * (search sur nom/slug/bio) sans charger tout en mémoire.
 */
public interface CreatorProfileRepository extends JpaRepository<CreatorProfile, Long>,
        JpaSpecificationExecutor<CreatorProfile> {

    /** Fiche publique d'une chaîne via son slug — ex. "/@ada-chain". */
    Optional<CreatorProfile> findBySlug(String slug);

    /** Fiche créateur d'un compte donné (relation 1-1 côté créateur). */
    Optional<CreatorProfile> findByUserId(Long userId);

    /** Vrai si ce compte possède déjà une fiche créateur. */
    boolean existsByUserId(Long userId);

    /** Vrai si le slug est déjà pris (contrôle d'unicité à la création de chaîne). */
    boolean existsBySlug(String slug);

    /** Chaînes par statut (pilotage admin). */
    long countByStatus(CreatorProfile.Status status);

    /**
     * Recherche admin paginée (étape 17) : texte (nom, slug, bio) + statut
     * optionnel.
     */
    @Query("""
            select c from CreatorProfile c
             where (:search is null
                    or lower(c.displayName) like lower(concat('%', :search, '%'))
                    or lower(c.slug) like lower(concat('%', :search, '%')))
               and (:status is null or c.status = :status)
            """)
    Page<CreatorProfile> searchAdmin(@Param("search") String search,
                                     @Param("status") CreatorProfile.Status status,
                                     Pageable pageable);
}