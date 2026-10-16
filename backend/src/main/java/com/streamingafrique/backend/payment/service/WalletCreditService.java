package com.streamingafrique.backend.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.notification.model.Notification;
import com.streamingafrique.backend.notification.service.NotificationService;
import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.wallet.model.Wallet;
import com.streamingafrique.backend.wallet.model.WalletTransaction;
import com.streamingafrique.backend.wallet.repository.WalletRepository;
import com.streamingafrique.backend.wallet.repository.WalletTransactionRepository;

/**
 * Crédit du wallet après paiement CONFIRMÉ (étape 14, §24-26).
 *
 * INVARIANT (pont argent réel → coins) : UN paiement CONFIRMED = UN crédit =
 * UNE transaction {@code COIN_PURCHASE}. Garanti par TROIS verrous combinés :
 *   1. la transition CONFIRMED n'a lieu qu'une fois (états terminaux) ;
 *   2. la clé {@code "purchase:<internalRef>"} UNIQUE en base rend le second
 *      crédit impossible (rejeu webhook, concurrence, reprise) ;
 *   3. le wallet est verrouillé en écriture (pas de solde perdu).
 *
 * Appelé DANS la transaction du webhook : jamais "Payment = SUCCESS sans
 * crédit" (échec du crédit = rollback complet, le webhook sera rejoué).
 */
@Service
public class WalletCreditService {

    private static final Logger log = LoggerFactory.getLogger(WalletCreditService.class);

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final NotificationService notifications;

    public WalletCreditService(WalletRepository wallets,
                               WalletTransactionRepository transactions,
                               NotificationService notifications) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.notifications = notifications;
    }

    /**
     * Crédite le payeur (crée le wallet à la volée au premier achat).
     *
     * @return true si un crédit a été appliqué, false si déjà crédité
     *         (webhook rejoué : succès idempotent, SANS second mouvement)
     */
    @Transactional
    public boolean creditFor(Payment payment) {
        String key = idempotencyKey(payment);
        if (transactions.existsByIdempotencyKey(key)) {
            log.info("Crédit déjà appliqué (internalRef={}) — rejeu ignoré",
                    payment.getInternalRef());
            return false;
        }
        User payer = payment.getPayer();
        Wallet wallet = wallets.lockByOwnerId(payer.getId())
                .orElseGet(() -> wallets.save(Wallet.create(payer)));
        long before = wallet.getBalanceCoins();
        wallet.credit(payment.getCoinsCredited());
        WalletTransaction movement = WalletTransaction.record(key, wallet,
                WalletTransaction.Direction.CREDIT, payment.getCoinsCredited(),
                payment.getFiatAmount(), payment.getFiatCurrency(),
                before, wallet.getBalanceCoins(),
                WalletTransaction.Kind.COIN_PURCHASE, payment.getInternalRef());
        movement.confirm();
        transactions.save(movement);
        log.info("Wallet crédité (user={}, +{} coins, solde {} → {})",
                payer.getId(), payment.getCoinsCredited(), before, wallet.getBalanceCoins());
        notifications.notify(payer, Notification.Type.PAYMENT, "Paiement confirmé",
                "+" + payment.getCoinsCredited() + " coins ajoutés à votre portefeuille",
                "/wallet");
        return true;
    }

    /** Clé de crédit déterministe (UN crédit par paiement, pour toujours). */
    static String idempotencyKey(Payment payment) {
        return "purchase:" + payment.getInternalRef();
    }
}
