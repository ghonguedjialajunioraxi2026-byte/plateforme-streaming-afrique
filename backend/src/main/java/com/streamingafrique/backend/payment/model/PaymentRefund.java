package com.streamingafrique.backend.payment.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "PaymentRefund" → table {@code payment_refunds}.
 *
 * RÔLE : chaque remboursement (total ou partiel) d'un paiement confirmé —
 * historique immuable (une ligne par opération, jamais d'UPDATE du paiement
 * au-delà du statut). Soldé côté coins par une transaction REFUND.
 */
@Entity
@Table(name = "payment_refunds", indexes = {
        @Index(name = "idx_refunds_payment", columnList = "payment_id")
})
public class PaymentRefund extends BaseEntity {

    /** État : demandé → envoyé / échoué. */
    public enum Status {
        REQUESTED, SENT, FAILED
    }

    /** Le paiement remboursé — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /** Montant fiat remboursé (strictement positif, <= paiement). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fiatAmount;

    /** Coins débités en contrepartie (strictement positif). */
    @Column(nullable = false)
    private long coinsDebited;

    /** Motif (litige, fraude, geste commercial…). */
    @Column(nullable = false, length = 500)
    private String reason;

    /** Référence prestataire du remboursement — NULL tant que non attribuée. */
    @Column(length = 200)
    private String providerRef;

    /** État par défaut : demandé. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected PaymentRefund() {
    }

    /** Fabrique : montants positifs, motif obligatoire. */
    public static PaymentRefund request(Payment payment, BigDecimal fiatAmount, long coinsDebited, String reason) {
        if (payment == null) {
            throw new IllegalArgumentException("Le paiement est obligatoire");
        }
        if (fiatAmount == null || fiatAmount.signum() <= 0 || coinsDebited <= 0) {
            throw new IllegalArgumentException("Montants strictement positifs exigés");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Le motif du remboursement est obligatoire");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.payment = payment;
        refund.fiatAmount = fiatAmount;
        refund.coinsDebited = coinsDebited;
        refund.reason = reason;
        refund.status = Status.REQUESTED;
        return refund;
    }

    /** Envoi constaté (REQUESTED → SENT). */
    public void markSent(String providerRef) {
        if (this.status == Status.REQUESTED) {
            this.status = Status.SENT;
            this.providerRef = providerRef;
        }
    }

    /** Échec (REQUESTED → FAILED). */
    public void markFailed() {
        if (this.status == Status.REQUESTED) {
            this.status = Status.FAILED;
        }
    }

    public Payment getPayment() {
        return payment;
    }

    public BigDecimal getFiatAmount() {
        return fiatAmount;
    }

    public long getCoinsDebited() {
        return coinsDebited;
    }

    public String getReason() {
        return reason;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public Status getStatus() {
        return status;
    }
}
