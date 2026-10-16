package com.streamingafrique.backend.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.streamingafrique.backend.wallet.model.Wallet;

/**
 * Accès aux portefeuilles {wallets}. AUCUNE logique métier ici.
 */
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /** Portefeuille d'un compte (relation 1-1 côté portefeuille). */
    Optional<Wallet> findByOwnerId(Long ownerId);

    /** Vrai si ce compte possède déjà un portefeuille. */
    boolean existsByOwnerId(Long ownerId);

    /**
     * Portefeuille VERROUILLÉ en écriture (étape 14) : deux crédits concurrents
     * sur le même wallet se sérialisent — jamais de mise à jour perdue du
     * solde (le perdant attend, puis relit le solde à jour).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.owner.id = :ownerId")
    Optional<Wallet> lockByOwnerId(@Param("ownerId") Long ownerId);
}
