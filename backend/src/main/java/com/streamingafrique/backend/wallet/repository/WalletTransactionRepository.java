package com.streamingafrique.backend.wallet.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.streamingafrique.backend.wallet.model.WalletTransaction;

/**
 * Accès au journal {transactions}. Relevé par portefeuille + idempotence.
 * AUCUNE logique métier ici.
 */
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /** Relevé d'un portefeuille, récents d'abord. */
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    /** Relevé paginé (historique /wallet/transactions). */
    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    /** Rejeu idempotent : la ligne existe-t-elle déjà pour cette clé ? */
    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    /** Vrai si cette clé a déjà été journalisée (garde anti-doublon). */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Total des mouvements d'un portefeuille pour une nature et un état donnés
     * (dashboard créateur : revenus par source — cadeaux, abonnements…).
     * Agrégation SQL, jamais de chargement mémoire.
     */
    @Query("""
            select coalesce(sum(t.amountCoins), 0)
              from WalletTransaction t
             where t.wallet.id = :walletId
               and t.kind = :kind
               and t.status = :status
            """)
    long sumAmountByWalletIdAndKindAndStatus(@Param("walletId") Long walletId,
                                            @Param("kind") WalletTransaction.Kind kind,
                                            @Param("status") WalletTransaction.Status status);
}
