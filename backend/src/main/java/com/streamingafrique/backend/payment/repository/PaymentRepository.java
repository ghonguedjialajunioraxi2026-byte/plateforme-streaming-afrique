package com.streamingafrique.backend.payment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.payment.model.Payment;

/**
 * Accès aux paiements {payments}. Idempotence interne + webhook.
 * AUCUNE logique métier ici.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Paiement par référence interne (nos retries, notre compta). */
    Optional<Payment> findByInternalRef(String internalRef);

    /** Paiement par référence prestataire (rejeu webhook). */
    Optional<Payment> findByProviderRef(String providerRef);

    /** Paiements d'un utilisateur dans un état donné. */
    List<Payment> findByPayerIdAndStatus(Long payerId, Payment.Status status);

    /**
     * Rejeu idempotent de CRÉATION (étape 14) : même clé = même intention,
     * on retourne l'existant au lieu de créer un doublon.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** Historique d'un utilisateur, récents d'abord (paginé). */
    Page<Payment> findByPayerIdOrderByCreatedAtDesc(Long payerId, Pageable pageable);

    /** Abandonnés à expirer (scheduler) : états non terminaux créés avant la limite. */
    List<Payment> findByStatusInAndCreatedAtBefore(
            Collection<Payment.Status> statuses, Instant cutoff);

    /** Paiements par statut (pilotage admin). */
    long countByStatus(Payment.Status status);
}
