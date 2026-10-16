package com.streamingafrique.backend.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Check;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "Wallet" → table {@code wallets}.
 *
 * RÔLE : le solde courant de coins d'un utilisateur (lecture rapide).
 * La VÉRITÉ comptable = {@link WalletTransaction} : le solde est un cache
 * recalculable, toute variation passe par une ligne de journal. Soldes en
 * BIGINT d'unités minimales — jamais de flottant, jamais d'arrondi.
 *
 * Cohérence : CHECK soldes >= 0 et réservé <= solde en base + vérification
 * service (solde suffisant avant débit). Gel (FROZEN) sur fraude/litige.
 */
@Entity
@Table(name = "wallets", indexes = {
        @Index(name = "uk_wallets_owner", columnList = "owner_id", unique = true)
})
@Check(constraints = "balance_coins >= 0 AND reserved_coins >= 0 AND reserved_coins <= balance_coins")
public class Wallet extends BaseEntity {

    /** État du portefeuille : actif ou gelé. */
    public enum Status {
        ACTIVE, FROZEN
    }

    /** Le propriétaire — exactement un portefeuille par compte. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    /** Solde en coins (unités minimales). */
    @Column(nullable = false)
    private long balanceCoins = 0;

    /** Part réservée (retraits en cours, litiges). */
    @Column(nullable = false)
    private long reservedCoins = 0;

    /** État par défaut : actif. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Wallet() {
    }

    /** Fabrique : portefeuille vide rattaché à un compte existant. */
    public static Wallet create(User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("Le propriétaire est obligatoire");
        }
        Wallet wallet = new Wallet();
        wallet.owner = owner;
        wallet.balanceCoins = 0;
        wallet.reservedCoins = 0;
        wallet.status = Status.ACTIVE;
        return wallet;
    }

    /** Crédit (montant strictement positif, portefeuille actif). */
    public void credit(long amountCoins) {
        requireActive();
        if (amountCoins <= 0) {
            throw new IllegalArgumentException("Le crédit doit être strictement positif");
        }
        this.balanceCoins += amountCoins;
    }

    /** Débit (solde suffisant exigé — jamais négatif). */
    public void debit(long amountCoins) {
        requireActive();
        if (amountCoins <= 0) {
            throw new IllegalArgumentException("Le débit doit être strictement positif");
        }
        if (amountCoins > this.balanceCoins) {
            throw new IllegalArgumentException("Solde insuffisant");
        }
        this.balanceCoins -= amountCoins;
    }

    /** Gel / dégel (fraude, litige). */
    public void changeStatus(Status status) {
        this.status = status;
    }

    private void requireActive() {
        if (this.status != Status.ACTIVE) {
            throw new IllegalStateException("Portefeuille gelé : opération refusée");
        }
    }

    public User getOwner() {
        return owner;
    }

    public long getBalanceCoins() {
        return balanceCoins;
    }

    public long getReservedCoins() {
        return reservedCoins;
    }

    public Status getStatus() {
        return status;
    }
}
