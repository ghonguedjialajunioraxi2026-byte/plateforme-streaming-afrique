package com.streamingafrique.backend.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.streamingafrique.backend.payment.model.CoinPackage;

/**
 * Accès au catalogue des packs {coin_packages} — SOURCE DE VÉRITÉ serveur
 * des prix (étape 14) : le frontend n'envoie que {@code packageId}, jamais
 * prix/coins/devise. AUCUNE logique métier ici.
 */
public interface CoinPackageRepository extends JpaRepository<CoinPackage, Long> {

    /** Packs actifs triés pour la vitrine d'achat (triés par sortOrder). */
    List<CoinPackage> findByActiveTrueOrderBySortOrderAsc();

    /** Packs actifs dans une devise (étape 19 : vitrine par devise). */
    List<CoinPackage> findByActiveTrueAndPriceCurrencyOrderBySortOrderAsc(String priceCurrency);

    /** Pack par nom exact (seed idempotent, support). */
    Optional<CoinPackage> findByName(String name);
}
