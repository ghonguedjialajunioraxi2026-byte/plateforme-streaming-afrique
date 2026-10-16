package com.streamingafrique.backend.payment;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.payment.dto.CreatePaymentRequest;
import com.streamingafrique.backend.payment.dto.PaymentResponse;
import com.streamingafrique.backend.payment.model.CoinPackage;
import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.payment.repository.CoinPackageRepository;
import com.streamingafrique.backend.payment.repository.PaymentRepository;
import com.streamingafrique.backend.payment.service.PaymentService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Étape 14 — création des paiements (H2, transaction annulée).
 *
 * Couvre : pack inexistant/désactivé/valide, prix-serveur (aucun champ
 * prix/coins/userId dans la requête : la falsification est IMPOSSIBLE par
 * construction), payeur = JWT, idempotence de création (rejeu + concurrence
 * de clé, réutilisation abusive → 409), annulation, historique, anti-IDOR.
 */
@SpringBootTest
@Transactional
class PaymentPurchaseFlowTest {

    @Autowired
    private PaymentService payments;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CoinPackageRepository packs;
    @Autowired
    private UserRepository users;

    private User payer;
    private CurrentUser actor;
    private CoinPackage pack;

    @BeforeEach
    void setUp() {
        payer = users.save(User.create("acheteur@example.com", "Acheteur", "hash"));
        actor = CurrentUser.of(payer.getId(), payer.getEmail(), User.Role.USER);
        pack = packs.save(CoinPackage.create("Pack Test", 1_000L,
                new BigDecimal("1000.0000"), "XOF", 1, "Pack de test"));
    }

    private CreatePaymentRequest request(Long packageId, String key) {
        return new CreatePaymentRequest(packageId, Payment.Provider.WAVE,
                Payment.Method.MOBILE_MONEY, key);
    }

    @Test
    void creationValideLitLePackEnBase() {
        PaymentResponse response = payments.createPayment(request(pack.getId(), "cle-1"), actor);

        assertNotNull(response.id());
        assertNotNull(response.internalRef());
        // Prix / coins / devise = le PACK (serveur), pas la requête.
        assertEquals(0, response.fiatAmount().compareTo(new BigDecimal("1000.0000")));
        assertEquals("XOF", response.fiatCurrency());
        assertEquals(1_000L, response.coinsAmount());
        assertEquals("WAVE", response.provider());
        // Sandbox attribue une référence → PENDING (jamais CONFIRMED à la création).
        assertEquals(Payment.Status.PENDING.name(), response.status());
        assertNotNull(response.providerRef());

        Payment stored = paymentRepository.findByInternalRef(response.internalRef()).orElseThrow();
        assertEquals(payer.getId(), stored.getPayer().getId());
    }

    @Test
    void packInexistantRejete() {
        ApiException ex = assertThrows(ApiException.class,
                () -> payments.createPayment(request(999_999L, "cle-x"), actor));
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void packDesactiveRejete() {
        pack.deactivate();
        packs.saveAndFlush(pack);

        ApiException ex = assertThrows(ApiException.class,
                () -> payments.createPayment(request(pack.getId(), "cle-x"), actor));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void memeCleDeuxFoisUnSeulPaiement() {
        PaymentResponse first = payments.createPayment(request(pack.getId(), "meme-cle"), actor);
        PaymentResponse second = payments.createPayment(request(pack.getId(), "meme-cle"), actor);

        assertEquals(first.id(), second.id());
        assertEquals(first.internalRef(), second.internalRef());
        assertEquals(1, paymentRepository.findAll().size());
    }

    @Test
    void cleReutiliseePourAutrePackConflit() {
        payments.createPayment(request(pack.getId(), "cle-partagee"), actor);
        CoinPackage other = packs.save(CoinPackage.create("Autre", 500L,
                new BigDecimal("500.0000"), "XOF", 2, null));

        ApiException ex = assertThrows(ApiException.class,
                () -> payments.createPayment(request(other.getId(), "cle-partagee"), actor));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void cleReutiliseeParAutreUtilisateurConflit() {
        payments.createPayment(request(pack.getId(), "cle-volee"), actor);
        User other = users.save(User.create("autre@example.com", "Autre", "hash"));

        ApiException ex = assertThrows(ApiException.class,
                () -> payments.createPayment(request(pack.getId(), "cle-volee"),
                        CurrentUser.of(other.getId(), other.getEmail(), User.Role.USER)));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void sansCleChaqueAppelCreeUnPaiement() {
        PaymentResponse first = payments.createPayment(request(pack.getId(), null), actor);
        PaymentResponse second = payments.createPayment(request(pack.getId(), null), actor);

        assertTrue(!first.id().equals(second.id()));
        assertEquals(2, paymentRepository.findAll().size());
    }

    @Test
    void nonAuthentifieRefuse() {
        ApiException ex = assertThrows(ApiException.class,
                () -> payments.createPayment(request(pack.getId(), "cle-anon"), null));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void annulationProprietaire() {
        PaymentResponse created = payments.createPayment(request(pack.getId(), "cle-annul"), actor);

        PaymentResponse cancelled = payments.cancelPayment(created.id(), actor);

        assertEquals(Payment.Status.CANCELLED.name(), cancelled.status());
    }

    @Test
    void detailAutreUtilisateurInterditSaufAdmin() {
        PaymentResponse created = payments.createPayment(request(pack.getId(), "cle-idor"), actor);
        User stranger = users.save(User.create("inconnu@example.com", "Inconnu", "hash"));
        User admin = users.save(User.create("admin@example.com", "Admin", "hash"));
        admin.changeRole(User.Role.ADMIN);

        ApiException ex = assertThrows(ApiException.class,
                () -> payments.getPayment(created.id(),
                        CurrentUser.of(stranger.getId(), stranger.getEmail(), User.Role.USER)));
        assertEquals(403, ex.getStatus().value());

        PaymentResponse seen = payments.getPayment(created.id(),
                CurrentUser.of(admin.getId(), admin.getEmail(), User.Role.ADMIN));
        assertEquals(created.id(), seen.id());
    }

    @Test
    void historiquePagine() {
        payments.createPayment(request(pack.getId(), "h1"), actor);
        payments.createPayment(request(pack.getId(), "h2"), actor);

        ApiPageResponse<PaymentResponse> page =
                payments.myHistory(actor, PageRequest.of(0, 10));

        assertEquals(2, page.totalElements());
        assertEquals(2, page.content().size());
    }

    @Test
    void supervisionReserveeAdmin() {
        ApiException ex = assertThrows(ApiException.class,
                () -> payments.allPayments(actor, PageRequest.of(0, 10)));
        assertEquals(403, ex.getStatus().value());

        User admin = users.save(User.create("admin2@example.com", "Admin2", "hash"));
        admin.changeRole(User.Role.ADMIN);
        ApiPageResponse<PaymentResponse> page = payments.allPayments(
                CurrentUser.of(admin.getId(), admin.getEmail(), User.Role.ADMIN),
                PageRequest.of(0, 10));
        assertTrue(page.totalElements() >= 0);
    }

    @Test
    void requeteNePorteNiPrixNiCoinsNiUser() {
        // Garantie structurelle §17 : ces champs n'existent pas dans le DTO,
        // la falsification est impossible par construction (pas seulement ignorée).
        List<String> fields = java.util.Arrays.stream(CreatePaymentRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertEquals(List.of("packageId", "provider", "method", "idempotencyKey"), fields);
    }
}
