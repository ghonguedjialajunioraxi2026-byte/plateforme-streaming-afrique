package com.streamingafrique.backend.wallet.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.wallet.model.Currency;

/**
 * Accès au référentiel monétaire {currencies}. AUCUNE logique métier ici.
 */
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    /** Versions d'une monnaie (historique des taux). */
    List<Currency> findByCodeOrderByEffectiveFromDesc(String code);
}
