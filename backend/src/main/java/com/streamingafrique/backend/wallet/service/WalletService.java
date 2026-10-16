package com.streamingafrique.backend.wallet.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.wallet.dto.WalletResponse;
import com.streamingafrique.backend.wallet.dto.WalletTransactionResponse;
import com.streamingafrique.backend.wallet.model.Wallet;
import com.streamingafrique.backend.wallet.model.WalletTransaction;
import com.streamingafrique.backend.wallet.repository.WalletRepository;
import com.streamingafrique.backend.wallet.repository.WalletTransactionRepository;

/**
 * Lecture du portefeuille (étape 14) : solde + journal.
 *
 * Le wallet est créé à la volée à la première lecture (solde zéro) — aucun
 * seed d'utilisateurs, aucune écriture comptable ici (les mouvements naissent
 * dans {@link com.streamingafrique.backend.payment.service.WalletCreditService}
 * et les futures dépenses cadeaux/abonnements).
 */
@Service
public class WalletService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository movements;
    private final UserRepository users;

    public WalletService(WalletRepository wallets, WalletTransactionRepository movements,
                         UserRepository users) {
        this.wallets = wallets;
        this.movements = movements;
        this.users = users;
    }

    /** MON solde (JWT — jamais de userId frontend). */
    @Transactional
    public WalletResponse myWallet(CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        return WalletResponse.from(walletOf(actor.id()));
    }

    /** MON journal, récent d'abord (paginé ; vide si aucun mouvement). */
    @Transactional(readOnly = true)
    public ApiPageResponse<WalletTransactionResponse> myTransactions(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        return wallets.findByOwnerId(actor.id())
                .map(wallet -> {
                    Page<WalletTransaction> page =
                            movements.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
                    return ApiPageResponse.from(page, page.getContent().stream()
                            .map(WalletTransactionResponse::from).toList());
                })
                .orElseGet(() -> ApiPageResponse.of(java.util.List.of(),
                        pageable.getPageNumber(), pageable.getPageSize(), 0));
    }

    private Wallet walletOf(Long userId) {
        return wallets.findByOwnerId(userId).orElseGet(() -> {
            User owner = users.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
            return wallets.save(Wallet.create(owner));
        });
    }
}
