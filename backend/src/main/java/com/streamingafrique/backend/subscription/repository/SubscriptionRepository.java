package com.streamingafrique.backend.subscription.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.subscription.model.Subscription;

/**
 * Accès aux abonnements {subscriptions}.
 *
 * L'unicité « un seul ACTIVE par (abonné, chaîne) » est une règle de
 * SERVICE (vérifiée avant création) adossée à un futur index partiel DDL
 * — pas une contrainte UNIQUE globale, qui interdirait l'historique
 * (réabonnements successifs). AUCUNE logique métier ici.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** Abonnements d'un utilisateur dans un statut donné. */
    List<Subscription> findBySubscriberIdAndStatus(Long subscriberId, Subscription.Status status);

    /** Abonnements reçus par une chaîne dans un statut donné. */
    List<Subscription> findByCreatorIdAndStatus(Long creatorId, Subscription.Status status);

    /** Vrai si un abonnement existe déjà dans ce statut (garde anti-doublon ACTIVE). */
    boolean existsBySubscriberIdAndCreatorIdAndStatus(Long subscriberId, Long creatorId,
                                                      Subscription.Status status);

    /** Nombre d'abonnements reçus par une chaîne dans un statut donné (dashboard). */
    long countByCreatorIdAndStatus(Long creatorId, Subscription.Status status);

    /** Abonnements par statut (pilotage admin). */
    long countByStatus(Subscription.Status status);
}
