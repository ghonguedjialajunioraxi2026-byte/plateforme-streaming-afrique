package com.streamingafrique.backend.payment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.payment.dto.CoinPackageResponse;
import com.streamingafrique.backend.payment.model.CoinPackage;
import com.streamingafrique.backend.payment.repository.CoinPackageRepository;

/**
 * Catalogue des packs de coins (étape 14, §14-15) — LECTURE SEULE : la
 * source de vérité des prix. Le pack ACTIF est relu en base à chaque
 * création de paiement (jamais de prix frontend).
 */
@Service
public class CoinPackageService {

    private final CoinPackageRepository repository;

    public CoinPackageService(CoinPackageRepository repository) {
        this.repository = repository;
    }

    /** Packs actifs triés (vitrine d'achat). */
    @Transactional(readOnly = true)
    public List<CoinPackageResponse> listActive() {
        return listActive(null);
    }

    /**
     * Packs actifs, filtrés par devise ISO-3 si fournie (étape 19 : la
     * vitrine affiche les prix dans la devise du pays — montants LUS
     * depuis la base, jamais depuis le frontend).
     */
    @Transactional(readOnly = true)
    public List<CoinPackageResponse> listActive(String currency) {
        List<com.streamingafrique.backend.payment.model.CoinPackage> packs;
        if (currency != null && !currency.isBlank()) {
            String norm = currency.trim().toUpperCase(java.util.Locale.ROOT);
            if (!norm.matches("[A-Z]{3}")) {
                throw com.streamingafrique.backend.exception.ApiException.badRequest(
                        "Devise invalide (ISO-3 attendue)");
            }
            packs = repository.findByActiveTrueAndPriceCurrencyOrderBySortOrderAsc(norm);
        } else {
            packs = repository.findByActiveTrueOrderBySortOrderAsc();
        }
        return packs.stream()
                .map(CoinPackageResponse::from)
                .toList();
    }

    /** Pack actif par id (404 si inexistant, 400 si désactivé). */
    @Transactional(readOnly = true)
    public CoinPackage requireActive(Long packageId) {
        if (packageId == null) {
            throw ApiException.badRequest("Le pack est obligatoire");
        }
        CoinPackage pack = repository.findById(packageId)
                .orElseThrow(() -> ApiException.notFound("Pack introuvable (id=" + packageId + ")"));
        if (!pack.isActive()) {
            throw ApiException.badRequest("Ce pack n'est plus disponible");
        }
        return pack;
    }
}
