package com.streamingafrique.backend.payment;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.payment.model.PaymentRefund;
import com.streamingafrique.backend.payment.repository.PaymentRefundRepository;
import com.streamingafrique.backend.payment.repository.PaymentRepository;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sous-étape 7.22 : paiements externes multi-prestataires — cycle de vie,
 * idempotence, remboursement, absence de données sensibles (H2, transaction
 * annulée). AUCUNE intégration fournisseur développée ici.
 */
@SpringBootTest
@Transactional
class PaymentStructureTest {

    @Autowired
    private UserRepository users;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private PaymentRefundRepository refunds;

    @Test
    void cycle_wave_initie_confirme_puis_rembourse() {
        User payer = users.save(User.create("payeur@example.com", "payeur", "hash"));
        Payment payment = payments.save(Payment.initiate(payer, new BigDecimal("1000.0000"), "XOF",
                2000, Payment.Method.MOBILE_MONEY, Payment.Provider.WAVE,
                "+221 77 *** 123", "{\"pays\":\"SN\"}"));

        assertEquals(Payment.Status.INITIATED, payment.getStatus());
        assertNotNull(payment.getInternalRef());

        payment.attachProviderRef("wave-tx-1");
        payment.confirm();
        payments.saveAndFlush(payment);
        assertEquals(Payment.Status.CONFIRMED,
                payments.findByProviderRef("wave-tx-1").orElseThrow().getStatus());

        PaymentRefund refund = refunds.save(PaymentRefund.request(payment,
                new BigDecimal("1000.0000"), 2000, "Double débit avéré"));
        refund.markSent("wave-rf-1");
        refunds.saveAndFlush(refund);
        payment.markRefunded();
        payments.saveAndFlush(payment);

        assertEquals(Payment.Status.REFUNDED, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1, refunds.findByPaymentId(payment.getId()).size());
    }

    @Test
    void echec_motive_et_montants_invalides_refuses() {
        User payer = users.save(User.create("echec@example.com", "echec", "hash"));
        Payment payment = payments.save(Payment.initiate(payer, new BigDecimal("5.0000"), "EUR",
                100, Payment.Method.CARD, Payment.Provider.STRIPE, "**** 4242", null));

        assertThrows(IllegalArgumentException.class, () -> payment.fail("  "));
        payment.fail("Fonds insuffisants");
        payments.saveAndFlush(payment);
        assertEquals("Fonds insuffisants",
                payments.findById(payment.getId()).orElseThrow().getFailureReason());

        assertThrows(IllegalArgumentException.class, () -> Payment.initiate(payer,
                new BigDecimal("0.0000"), "XOF", 10, Payment.Method.MOBILE_MONEY, Payment.Provider.WAVE, null, null));
        assertThrows(IllegalArgumentException.class, () -> Payment.initiate(payer,
                new BigDecimal("10.0000"), "XO", 10, Payment.Method.MOBILE_MONEY, Payment.Provider.WAVE, null, null));
    }

    @Test
    void aucune_donnee_bancaire_sensible_stockee() {
        // PAN, CVV, numéros complets : aucun champ ne doit exister.
        Set<String> champs = Arrays.stream(Payment.class.getDeclaredFields())
                .map(f -> f.getName().toLowerCase()).collect(Collectors.toSet());
        champs.addAll(Arrays.stream(PaymentRefund.class.getDeclaredFields())
                .map(f -> f.getName().toLowerCase()).collect(Collectors.toSet()));
        for (String interdit : new String[]{"pan", "cvv", "cvc", "cardnumber", "numero", "cryptogramme"}) {
            assertTrue(champs.stream().noneMatch(c -> c.contains(interdit)),
                    "champ sensible interdit détecté : " + interdit);
        }
    }
}
