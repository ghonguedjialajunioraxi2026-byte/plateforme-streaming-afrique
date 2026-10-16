package com.streamingafrique.backend.stream.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.streamingafrique.backend.stream.model.Stream;

/**
 * Accès aux directs {streams}.
 *
 * Méthodes JUSTE nécessaires : les streams d'une chaîne, les streams
 * par statut ("en direct", "programmés"...). Le tri et la pagination
 * sont passés PAR L'APPELANT via Pageable (au choix d'ici, ordre de tri,
 * page demandée) — le repository ne décide de rien.
 *
 * {@link JpaSpecificationExecutor} permet la recherche combinée dynamique
 * (Specification : search + creatorId + catégorie + statut) sans charger
 * plus que la page en mémoire.
 */
public interface StreamRepository extends JpaRepository<Stream, Long>, JpaSpecificationExecutor<Stream> {

    /** Les directs d'un créateur (sidebar d'une chaîne), paginés. */
    Page<Stream> findByCreatorId(Long creatorId, Pageable pageable);

    /** Les directs par statut (ex. page "en direct"), paginés. */
    Page<Stream> findByStatus(Stream.Status status, Pageable pageable);

    /** Comptage par statut — alimente la statistique courte durée « directs en cours » (cache). */
    long countByStatus(Stream.Status status);

    /** Résolution ingest : le direct portant cette empreinte de clé (étape 10). */
    Optional<Stream> findByStreamKeyHash(String streamKeyHash);

    /** Garde de concurrence : la chaîne a-t-elle déjà un direct à ce statut ? */
    boolean existsByCreatorIdAndStatus(Long creatorId, Stream.Status status);

    /** Directs d'une chaîne à un statut donné (ex. le LIVE courant), paginés. */
    Page<Stream> findByCreatorIdAndStatus(Long creatorId, Stream.Status status, Pageable pageable);

    /** Candidats du réconciliateur : directs à un statut, démarrés avant un instant. */
    List<Stream> findByStatusAndStartedAtBefore(Stream.Status status, Instant before);

    /** Nombre de directs d'une chaîne (dashboard créateur). */
    long countByCreatorId(Long creatorId);

    /** Nombre de directs d'une chaîne à un statut donné (dashboard créateur). */
    long countByCreatorIdAndStatus(Long creatorId, Stream.Status status);

    /** Le direct le plus récent d'une chaîne à un statut donné (ex. LIVE courant). */
    Optional<Stream> findFirstByCreatorIdAndStatusOrderByStartedAtDesc(Long creatorId,
                                                                       Stream.Status status);
}