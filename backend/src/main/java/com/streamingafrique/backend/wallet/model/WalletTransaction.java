package com.streamingafrique.backend.wallet.model;

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

import org.hibernate.annotations.Check;

import com.streamingafrique.backend.common.model.BaseEntity;

/**
 * Entité "WalletTransaction" → table {@code transactions}.
 *
 * RÔLE : le JOURNAL comptable unique (sous-étape 7.21) — seule vérité des
 * mouvements de valeur. Append-only : on contre-passe (nouvelle ligne
 * inverse), on ne modifie JAMAIS une ligne.
 *
 * Précision : montants et soldes en BIGINT de coins (arithmétique entière
 * exacte). Le fiat n'apparaît qu'en RÉFÉRENCE ({@code NUMERIC(19,4)} +
 * code ISO) pour la comptabilité — jamais dans le calcul des soldes.
 *
 * Idempotence : {@code idempotencyKey} UNIQUE — tout retry (webhook,
 * double-clic, reprise) rejoue la même clé au lieu de dupliquer le
 * mouvement. Cohérence : soldes avant/après figés par ligne (>= 0).
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "uk_tx_idempotency", columnList = "idempotencyKey", unique = true),
        @Index(name = "idx_tx_wallet_id", columnList = "wallet_id,id")
})
@Check(constraints = "amount_coins > 0 AND balance_before >= 0 AND balance_after >= 0")
public class WalletTransaction extends BaseEntity {

    /** Sens du mouvement pour ce portefeuille. */
    public enum Direction {
        CREDIT, DEBIT
    }

    /** Nature du mouvement (un type par fait métier). */
    public enum Kind {
        COIN_PURCHASE, GIFT_SEND, GIFT_RECEIPT, FEE,
        SUBSCRIPTION_CHARGE, SUBSCRIPTION_PAYOUT, WITHDRAWAL, REFUND, AD_PAYOUT
    }

    /** État : en attente → succès / échec ; succès → contre-passé. */
    public enum Status {
        PENDING, SUCCESS, FAILED, REVERSED
    }

    /** Clé d'idempotence (webhook, retry) — unique. */
    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /** Le portefeuille imputé — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    /** Montant en coins (unités minimales, strictement positif). */
    @Column(nullable = false)
    private long amountCoins;

    /** Référence fiat (compta uniquement) — NULL = pur mouvement coins. */
    @Column(precision = 19, scale = 4)
    private BigDecimal fiatReferenceAmount;

    /** Devise ISO de la référence (ex. "XOF") — cohérente avec le montant. */
    @Column(length = 3)
    private String fiatCurrency;

    /** Soldes figés par la ligne (audit, recalcul). */
    @Column(nullable = false)
    private long balanceBefore;

    @Column(nullable = false)
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    /** État par défaut : en attente (confirmé par le service). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** Lien logique vers le fait source (paiement, cadeau, abonnement…). */
    @Column(length = 200)
    private String reference;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected WalletTransaction() {
    }

    /** Fabrique : montant positif, cohérence fiat, soldes >= 0. */
    public static WalletTransaction record(String idempotencyKey, Wallet wallet, Direction direction,
                                           long amountCoins, BigDecimal fiatReferenceAmount, String fiatCurrency,
                                           long balanceBefore, long balanceAfter,
                                           Kind kind, String reference) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || wallet == null
                || direction == null || kind == null) {
            throw new IllegalArgumentException("Clé, portefeuille, sens et nature sont obligatoires");
        }
        if (amountCoins <= 0 || balanceBefore < 0 || balanceAfter < 0) {
            throw new IllegalArgumentException("Montant positif et soldes positifs exigés");
        }
        if ((fiatReferenceAmount == null) != (fiatCurrency == null)) {
            throw new IllegalArgumentException("Montant et devise fiat vont ensemble");
        }
        WalletTransaction transaction = new WalletTransaction();
        transaction.idempotencyKey = idempotencyKey;
        transaction.wallet = wallet;
        transaction.direction = direction;
        transaction.amountCoins = amountCoins;
        transaction.fiatReferenceAmount = fiatReferenceAmount;
        transaction.fiatCurrency = fiatCurrency;
        transaction.balanceBefore = balanceBefore;
        transaction.balanceAfter = balanceAfter;
        transaction.kind = kind;
        transaction.reference = reference;
        transaction.status = Status.PENDING;
        return transaction;
    }

    /** Confirmation (PENDING → SUCCESS). */
    public void confirm() {
        if (this.status == Status.PENDING) {
            this.status = Status.SUCCESS;
        }
    }

    /** Échec (PENDING → FAILED). */
    public void fail() {
        if (this.status == Status.PENDING) {
            this.status = Status.FAILED;
        }
    }

    /** Contre-passation constatée (SUCCESS → REVERSED, la ligne inverse existe). */
    public void markReversed() {
        if (this.status == Status.SUCCESS) {
            this.status = Status.REVERSED;
        }
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountCoins() {
        return amountCoins;
    }

    public BigDecimal getFiatReferenceAmount() {
        return fiatReferenceAmount;
    }

    public String getFiatCurrency() {
        return fiatCurrency;
    }

    public long getBalanceBefore() {
        return balanceBefore;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Kind getKind() {
        return kind;
    }

    public Status getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
    }
}
