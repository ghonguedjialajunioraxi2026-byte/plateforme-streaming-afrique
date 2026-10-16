package com.streamingafrique.backend.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.payment.dto.CreatePaymentRequest;
import com.streamingafrique.backend.payment.dto.PaymentResponse;
import com.streamingafrique.backend.payment.model.CoinPackage;
import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.payment.provider.SandboxPaymentProvider;
import com.streamingafrique.backend.payment.provider.WebhookVerification;
import com.streamingafrique.backend.payment.repository.CoinPackageRepository;
import com.streamingafrique.backend.payment.repository.PaymentRepository;
import com.streamingafrique.backend.payment.service.PaymentService;
import com.streamingafrique.backend.payment.service.PaymentWebhookService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;
import com.streamingafrique.backend.wallet.model.WalletTransaction;
import com.streamingafrique.backend.wallet.repository.WalletRepository;
import com.streamingafrique.backend.wallet.repository.WalletTransactionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 14 — webhooks : TESTS CRITIQUES (H2, transactions annulées).
 *
 * Couvre : signature valide/invalide/absente, paiement inconnu, webhook
 * répété ×3 (UN seul crédit, UNE seule transaction), montant/devise
 * incorrects (rejet SANS changement d'état), paiement déjà terminal,
 * issue FAILED (aucun crédit), expiration des abandonnés.
 */
@SpringBootTest(properties = "app.payments.webhook-secret=test-secret-webhook-14")
@Transactional
class PaymentWebhookIdempotenceTest {

    @Autowired
    private PaymentService payments;
    @Autowired
    private PaymentWebhookService webhooks;
    @Autowired
    private SandboxPaymentProvider sandbox;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CoinPackageRepository packs;
    @Autowired
    private UserRepository users;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private WalletTransactionRepository movements;

    private User payer;
    private CurrentUser actor;
    private CoinPackage pack;

    @BeforeEach
    void setUp() {
        payer = users.save(User.create("webhook@example.com", "Webhook", "hash"));
        actor = CurrentUser.of(payer.getId(), payer.getEmail(), User.Role.USER);
        pack = packs.save(CoinPackage.create("Pack Webhook", 5_000L,
                new BigDecimal("5000.0000"), "XOF", 1, null));
    }

    private PaymentResponse initiate(String key) {
        return payments.createPayment(new CreatePaymentRequest(pack.getId(),
                Payment.Provider.WAVE, Payment.Method.MOBILE_MONEY, key), actor);
    }

    private String webhookBody(PaymentResponse payment, String status,
                               String amount, String currency) {
        return "{\"internalRef\":\"" + payment.internalRef() + "\","
                + "\"providerRef\":\"" + payment.providerRef() + "\","
                + "\"status\":\"" + status + "\","
                + "\"fiatAmount\":\"" + amount + "\","
                + "\"fiatCurrency\":\"" + currency + "\"}";
    }

    private PaymentWebhookService.WebhookOutcome confirm(PaymentResponse payment) {
        String raw = webhookBody(payment, "SUCCEEDED", "5000.0000", "XOF");
        return webhooks.handleWebhook("WAVE", raw, sandbox.sign(raw));
    }

    @Test
    void tripleWebhookUnSeulCreditUneSeuleTransaction() {
        PaymentResponse payment = initiate("triple");

        assertEquals(PaymentWebhookService.WebhookOutcome.CONFIRMED, confirm(payment));
        assertEquals(PaymentWebhookService.WebhookOutcome.ALREADY_PROCESSED, confirm(payment));
        assertEquals(PaymentWebhookService.WebhookOutcome.ALREADY_PROCESSED, confirm(payment));

        Payment stored = paymentRepository.findByInternalRef(payment.internalRef()).orElseThrow();
        assertEquals(Payment.Status.CONFIRMED, stored.getStatus());
        assertEquals(5_000L, wallets.findByOwnerId(payer.getId()).orElseThrow().getBalanceCoins());

        List<WalletTransaction> lines = movements.findAll().stream()
                .filter(m -> ("purchase:" + payment.internalRef()).equals(m.getIdempotencyKey()))
                .toList();
        assertEquals(1, lines.size());
        WalletTransaction line = lines.get(0);
        assertEquals(WalletTransaction.Direction.CREDIT, line.getDirection());
        assertEquals(5_000L, line.getAmountCoins());
        assertEquals(WalletTransaction.Kind.COIN_PURCHASE, line.getKind());
        assertEquals(WalletTransaction.Status.SUCCESS, line.getStatus());
        assertEquals(0L, line.getBalanceBefore());
        assertEquals(5_000L, line.getBalanceAfter());
        assertEquals(payment.internalRef(), line.getReference());
    }

    @Test
    void signatureInvalideRejeteeSansEtat() {
        PaymentResponse payment = initiate("sig-ko");
        String raw = webhookBody(payment, "SUCCEEDED", "5000.0000", "XOF");

        ApiException ex = assertThrows(ApiException.class,
                () -> webhooks.handleWebhook("WAVE", raw, "signature-fausse"));
        assertEquals(401, ex.getStatus().value());
        assertEquals(Payment.Status.PENDING,
                paymentRepository.findByInternalRef(payment.internalRef()).orElseThrow().getStatus());
        assertTrue(wallets.findByOwnerId(payer.getId()).isEmpty());
    }

    @Test
    void signatureAbsenteRejetee() {
        PaymentResponse payment = initiate("sig-abs");
        String raw = webhookBody(payment, "SUCCEEDED", "5000.0000", "XOF");

        ApiException ex = assertThrows(ApiException.class,
                () -> webhooks.handleWebhook("WAVE", raw, null));
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    void paiementInconnu404() {
        String raw = "{\"internalRef\":\"pay-inexistant-xyz\",\"providerRef\":\"sbx-1\","
                + "\"status\":\"SUCCEEDED\",\"fiatAmount\":\"5000.0000\",\"fiatCurrency\":\"XOF\"}";

        ApiException ex = assertThrows(ApiException.class,
                () -> webhooks.handleWebhook("WAVE", raw, sandbox.sign(raw)));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void montantIncorrectRejeteSansChangerLEtat() {
        PaymentResponse payment = initiate("montant-ko");
        String raw = webhookBody(payment, "SUCCEEDED", "1.0000", "XOF");

        ApiException ex = assertThrows(ApiException.class,
                () -> webhooks.handleWebhook("WAVE", raw, sandbox.sign(raw)));
        assertEquals(400, ex.getStatus().value());
        // État INTACT : le vrai webhook pourra encore confirmer.
        assertEquals(Payment.Status.PENDING,
                paymentRepository.findByInternalRef(payment.internalRef()).orElseThrow().getStatus());
        assertTrue(wallets.findByOwnerId(payer.getId()).isEmpty());
    }

    @Test
    void deviseIncorrecteRejetee() {
        PaymentResponse payment = initiate("devise-ko");
        String raw = webhookBody(payment, "SUCCEEDED", "5000.0000", "EUR");

        ApiException ex = assertThrows(ApiException.class,
                () -> webhooks.handleWebhook("WAVE", raw, sandbox.sign(raw)));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void webhookIncompletRejete() {
        String raw = "{\"internalRef\":\"pay-xyz\"}";

        ApiException ex = assertThrows(ApiException.class,
                () -> webhooks.handleWebhook("WAVE", raw, sandbox.sign(raw)));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void issueFailedMarqueeSansCredit() {
        PaymentResponse payment = initiate("echec");
        String raw = "{\"internalRef\":\"" + payment.internalRef() + "\","
                + "\"providerRef\":\"" + payment.providerRef() + "\","
                + "\"status\":\"FAILED\",\"fiatAmount\":\"5000.0000\","
                + "\"fiatCurrency\":\"XOF\",\"failureReason\":\"Fonds insuffisants\"}";

        assertEquals(PaymentWebhookService.WebhookOutcome.MARKED_FAILED,
                webhooks.handleWebhook("WAVE", raw, sandbox.sign(raw)));

        Payment stored = paymentRepository.findByInternalRef(payment.internalRef()).orElseThrow();
        assertEquals(Payment.Status.FAILED, stored.getStatus());
        assertEquals("Fonds insuffisants", stored.getFailureReason());
        assertTrue(wallets.findByOwnerId(payer.getId()).isEmpty());
    }

    @Test
    void paiementDejaEchoueIgnoreLeSuccesTardif() {
        PaymentResponse payment = initiate("tardif");
        Payment stored = paymentRepository.findByInternalRef(payment.internalRef()).orElseThrow();
        stored.fail("Abandon utilisateur");

        assertEquals(PaymentWebhookService.WebhookOutcome.ALREADY_PROCESSED, confirm(payment));
        assertTrue(wallets.findByOwnerId(payer.getId()).isEmpty());
    }

    @Test
    void simulateSandboxConfirmeEtCredite() {
        PaymentResponse payment = initiate("simu");

        PaymentWebhookService.WebhookOutcome outcome = webhooks.simulate(
                payment.internalRef(), WebhookVerification.Outcome.SUCCEEDED, actor);

        assertEquals(PaymentWebhookService.WebhookOutcome.CONFIRMED, outcome);
        assertEquals(5_000L, wallets.findByOwnerId(payer.getId()).orElseThrow().getBalanceCoins());
    }

    @Test
    void simulateAutreUtilisateurInterdite() {
        PaymentResponse payment = initiate("simu-idor");
        User stranger = users.save(User.create("etranger@example.com", "Etranger", "hash"));

        ApiException ex = assertThrows(ApiException.class,
                () -> webhooks.simulate(payment.internalRef(),
                        WebhookVerification.Outcome.SUCCEEDED,
                        CurrentUser.of(stranger.getId(), stranger.getEmail(), User.Role.USER)));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void abandonnesExpirentConfirmesIntacts() {
        PaymentResponse abandoned = initiate("abandon");
        PaymentResponse confirmed = initiate("confirme-puis-expire");
        confirm(confirmed);

        int expired = payments.expireStale(Instant.now().plusSeconds(60));

        assertEquals(1, expired);
        assertEquals(Payment.Status.EXPIRED,
                paymentRepository.findByInternalRef(abandoned.internalRef()).orElseThrow().getStatus());
        assertEquals(Payment.Status.CONFIRMED,
                paymentRepository.findByInternalRef(confirmed.internalRef()).orElseThrow().getStatus());
    }
}
