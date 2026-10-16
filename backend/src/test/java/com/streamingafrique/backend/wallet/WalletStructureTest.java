package com.streamingafrique.backend.wallet;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.creator.model.CreatorProfile;
import com.streamingafrique.backend.creator.repository.CreatorProfileRepository;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.wallet.model.Currency;
import com.streamingafrique.backend.wallet.model.GiftItem;
import com.streamingafrique.backend.wallet.model.GiftSend;
import com.streamingafrique.backend.wallet.model.Wallet;
import com.streamingafrique.backend.wallet.model.WalletTransaction;
import com.streamingafrique.backend.wallet.repository.GiftItemRepository;
import com.streamingafrique.backend.wallet.repository.GiftSendRepository;
import com.streamingafrique.backend.wallet.repository.WalletRepository;
import com.streamingafrique.backend.wallet.repository.WalletTransactionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.21 : monnaie interne, portefeuille, cadeaux, journal —
 * arithmétique entière exacte, cohérence des soldes, idempotence (H2,
 * transaction annulée). Aucun paiement externe développé ici.
 */
@SpringBootTest
@Transactional
class WalletStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private CreatorProfileRepository channels;
    @Autowired
    private StreamRepository streams;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private WalletTransactionRepository transactions;
    @Autowired
    private GiftItemRepository gifts;
    @Autowired
    private GiftSendRepository sends;
    @Autowired
    private EntityManager entityManager;

    private User compte(String email) {
        return users.save(User.create(email, email.split("@")[0], "hash"));
    }

    @Test
    void precision_entiere_sans_flottant() {
        // 199 + 99 = 298 EXACT en BIGINT (0.1 + 0.2 flottant donnerait 0.30000000000000004).
        User user = compte("riche@example.com");
        Wallet wallet = wallets.save(Wallet.create(user));
        wallet.credit(199);
        wallet.credit(99);
        wallets.saveAndFlush(wallet);
        assertEquals(298, wallets.findByOwnerId(user.getId()).orElseThrow().getBalanceCoins());

        wallet.debit(98);
        wallets.saveAndFlush(wallet);
        assertEquals(200, wallets.findById(wallet.getId()).orElseThrow().getBalanceCoins());
    }

    @Test
    void debit_insuffisant_et_portefeuille_gele_refuses() {
        User user = compte("pauvre@example.com");
        Wallet wallet = wallets.save(Wallet.create(user));
        assertThrows(IllegalArgumentException.class, () -> wallet.debit(1));

        wallet.credit(50);
        wallets.saveAndFlush(wallet);
        wallet.changeStatus(Wallet.Status.FROZEN);
        wallets.saveAndFlush(wallet);
        assertThrows(IllegalStateException.class, () -> wallet.debit(10));
        assertThrows(IllegalStateException.class, () -> wallet.credit(10));
    }

    @Test
    void journal_idempotent_et_cycle_confirmation() {
        User user = compte("journal@example.com");
        Wallet wallet = wallets.save(Wallet.create(user));

        WalletTransaction tx = transactions.save(WalletTransaction.record(
                "pay-001", wallet, WalletTransaction.Direction.CREDIT, 1000,
                new BigDecimal("500.0000"), "XOF", 0, 1000,
                WalletTransaction.Kind.COIN_PURCHASE, "provider-ref-1"));
        assertEquals(WalletTransaction.Status.PENDING, tx.getStatus());
        assertTrue(transactions.existsByIdempotencyKey("pay-001"));

        // Rejeu de la même clé = refus base (pas de double crédit).
        assertThrows(DataIntegrityViolationException.class, () -> transactions.saveAndFlush(
                WalletTransaction.record("pay-001", wallet, WalletTransaction.Direction.CREDIT, 1000,
                        new BigDecimal("500.0000"), "XOF", 0, 1000,
                        WalletTransaction.Kind.COIN_PURCHASE, "provider-ref-1")));
        // La session est polluée par l'échec : purge avant de continuer.
        entityManager.clear();
        tx = transactions.findByIdempotencyKey("pay-001").orElseThrow();

        tx.confirm();
        transactions.saveAndFlush(tx);
        assertEquals(WalletTransaction.Status.SUCCESS,
                transactions.findByIdempotencyKey("pay-001").orElseThrow().getStatus());
        tx.markReversed();
        transactions.saveAndFlush(tx);
        assertEquals(WalletTransaction.Status.REVERSED,
                transactions.findById(tx.getId()).orElseThrow().getStatus());
    }

    @Test
    void cadeau_catalogue_envoi_cout_fige() {
        User fan = compte("fan-coin@example.com");
        User host = compte("host-coin@example.com");
        CreatorProfile channel = channels.save(CreatorProfile.create(host, "Host TV", "host-tv"));
        Stream live = streams.save(Stream.schedule(channel, "Live", null, Instant.now()));

        assertThrows(IllegalArgumentException.class, () -> GiftItem.create("Gratuit", 0, null));
        GiftItem rose = gifts.save(GiftItem.create("Rose", 10, "rose.json"));
        assertTrue(gifts.findByActiveTrue().stream().anyMatch(g -> g.getName().equals("Rose")));

        GiftSend envoi = sends.save(GiftSend.send(rose, fan, live, 3, 24));
        assertEquals(30, envoi.getTotalCostCoins());
        assertEquals(24, envoi.getCreatorShareCoins());
        assertEquals(1, sends.findByStreamIdOrderByCreatedAtAsc(live.getId()).size());

        // Part > total et quantité nulle refusés.
        assertThrows(IllegalArgumentException.class, () -> GiftSend.send(rose, fan, live, 1, 11));
        assertThrows(IllegalArgumentException.class, () -> GiftSend.send(rose, fan, live, 0, 0));

        Currency coin = Currency.create("COIN", "Coins", new BigDecimal("500.0000"),
                new BigDecimal("350.0000"), 1000);
        assertEquals("COIN", coin.getCode());
        assertEquals(1000, coin.getFeeBps());
        assertThrows(IllegalArgumentException.class, () -> Currency.create("X", "X",
                new BigDecimal("0.0000"), new BigDecimal("1.0000"), 0));
        assertThrows(IllegalArgumentException.class, () -> Currency.create("X", "X",
                new BigDecimal("1.0000"), new BigDecimal("1.0000"), 10001));
    }
}
