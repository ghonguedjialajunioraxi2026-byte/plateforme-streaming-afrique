package com.streamingafrique.backend.subscription.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.subscription.model.SubscriptionPlan;

/**
 * Accès au catalogue des formules {subscription_plans}.
 * AUCUNE logique métier ici.
 */
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    /** Formule par nom commercial. */
    Optional<SubscriptionPlan> findByName(String name);

    /** Vrai si ce nom de formule existe déjà. */
    boolean existsByName(String name);
}
