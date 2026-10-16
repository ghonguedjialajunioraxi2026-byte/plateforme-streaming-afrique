package com.streamingafrique.backend.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.payment.model.PaymentRefund;

/**
 * Accès aux remboursements {payment_refunds}. AUCUNE logique métier ici.
 */
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {

    /** Remboursements d'un paiement (historique). */
    List<PaymentRefund> findByPaymentId(Long paymentId);
}
