package com.streamingafrique.backend.wallet.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;
import com.streamingafrique.backend.wallet.dto.WalletResponse;
import com.streamingafrique.backend.wallet.dto.WalletTransactionResponse;
import com.streamingafrique.backend.wallet.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller REST du portefeuille (étape 14) :
 *   GET /api/wallet/me           → MON solde (AUTHENTICATED, créé à la volée)
 *   GET /api/wallet/transactions → MON journal paginé (AUTHENTICATED)
 *
 * Lecture seule : les écritures naissent dans le crédit webhook (achats) et
 * les futures dépenses (cadeaux, abonnements) — jamais ici.
 */
@Tag(name = "Portefeuille", description = "Solde de coins et journal : "
        + "lecture du propriétaire (le crédit naît du webhook, §24).")
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @Operation(summary = "Mon solde",
            description = "AUTHENTICATED (JWT — jamais de userId). Crée le "
                    + "portefeuille vide à la première lecture.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`WalletResponse` (`balanceCoins`, `availableCoins`…)"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/me")
    public ApiResponse<WalletResponse> me(Authentication authentication) {
        return ApiResponse.success(wallets.myWallet(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Mon journal",
            description = "AUTHENTICATED, paginé (récent d'abord). Chaque ligne "
                    + "porte la référence du fait source (ex. internalRef `pay-…`).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `WalletTransactionResponse`"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/transactions")
    public ApiResponse<ApiPageResponse<WalletTransactionResponse>> transactions(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Mes transactions",
                wallets.myTransactions(CurrentUser.from(authentication), pageable));
    }
}
