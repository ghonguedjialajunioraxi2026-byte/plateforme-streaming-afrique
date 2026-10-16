package com.streamingafrique.backend.payment.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.payment.dto.CoinPackageResponse;
import com.streamingafrique.backend.payment.service.CoinPackageService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller REST du catalogue des packs (étape 14) :
 *   GET /api/coin-packages → packs actifs triés (AUTHENTICATED)
 *
 * Lecture seule : les prix viennent de la base (source de vérité serveur).
 */
@Tag(name = "Packs de coins", description = "Catalogue d'achat : packs actifs "
        + "triés (prix/coins relus en base à chaque paiement, jamais frontend).")
@RestController
@RequestMapping("/api/coin-packages")
public class CoinPackageController {

    private final CoinPackageService packs;

    public CoinPackageController(CoinPackageService packs) {
        this.packs = packs;
    }

    @Operation(summary = "Packs disponibles",
            description = "AUTHENTICATED. Vitrine d'achat : packs actifs triés "
                    + "(`id, name, coinsAmount, priceAmount, priceCurrency`).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Liste des packs actifs"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping
    public ApiResponse<List<CoinPackageResponse>> list(
            @RequestParam(required = false) String currency,
            Authentication authentication) {
        CurrentUser.requireAuthenticated(CurrentUser.from(authentication));
        return ApiResponse.success("Packs disponibles", packs.listActive(currency));
    }
}
