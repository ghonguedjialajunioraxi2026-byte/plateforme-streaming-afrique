package com.streamingafrique.backend.payment.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.payment.dto.CreatePaymentRequest;
import com.streamingafrique.backend.payment.dto.PaymentResponse;
import com.streamingafrique.backend.payment.model.CoinPackage;
import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.payment.provider.PaymentProvider;
import com.streamingafrique.backend.payment.provider.PaymentProviderRegistry;
import com.streamingafrique.backend.payment.provider.ProviderInitiation;
import com.streamingafrique.backend.payment.repository.PaymentRepository;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.user.model.User;
import com.streamingafrique.backend.user.repository.UserRepository;

/**
 * Création et suivi des paiements (étape 14, §16).
 *
 * SÉCURITÉ (ne jamais faire confiance au frontend, §17) : le payeur vient du
 * JWT ({@link CurrentUser}), le prix/les coins/la devise viennent du pack en
 * base ({@link CoinPackageService}) — la requête ne porte QUE l'intention
 * (packageId + prestataire + moyen + clé d'idempotence). Aucun crédit ici :
 * le wallet n'est crédité qu'après confirmation vérifiée (webhook).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final UserRepository users;
    private final CoinPackageService packs;
    private final PaymentProviderRegistry registry;

    public PaymentService(PaymentRepository payments, UserRepository users,
                          CoinPackageService packs, PaymentProviderRegistry registry) {
        this.payments = payments;
        this.users = users;
        this.packs = packs;
        this.registry = registry;
    }

    /**
     * Crée un paiement (INITIATED → PENDING après attribution prestataire).
     * Idempotent : même clé = même paiement (retourne l'existant, jamais de
     * doublon — y compris en concurrence, via la contrainte UNIQUE).
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        CoinPackage pack = packs.requireActive(request.packageId());
        PaymentProvider provider = registry.resolve(request.provider());
        if (!provider.supportedMethods().contains(request.method())) {
            throw ApiException.badRequest("Moyen " + request.method()
                    + " non supporté par " + request.provider());
        }
        String key = normalizedKey(request.idempotencyKey());
        if (key != null) {
            Payment existing = payments.findByIdempotencyKey(key).orElse(null);
            if (existing != null) {
                checkSameIntention(existing, actor, pack, request);
                log.info("Création rejouée (idempotencyKey={}) → paiement existant {}",
                        maskKey(key), existing.getInternalRef());
                return PaymentResponse.from(existing);
            }
        }
        User payer = users.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Compte introuvable"));
        Payment payment = Payment.initiate(payer, pack.getPriceAmount(), pack.getPriceCurrency(),
                pack.getCoinsAmount(), request.method(), request.provider(), null, null, key);
        ProviderInitiation initiation = provider.initiate(payment);
        if (initiation.providerRef() != null && !initiation.providerRef().isBlank()) {
            payment.attachProviderRef(initiation.providerRef().trim());
        }
        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException concurrent) {
            // Concurrence : le doublon a gagné la course — on retourne l'existant.
            Payment raced = key == null ? null : payments.findByIdempotencyKey(key).orElse(null);
            if (raced != null) {
                log.info("Création concurrente (idempotencyKey={}) → paiement existant {}",
                        maskKey(key), raced.getInternalRef());
                checkSameIntention(raced, actor, pack, request);
                return PaymentResponse.from(raced);
            }
            throw ApiException.conflict("Paiement déjà enregistré");
        }
        log.info("Paiement créé (internalRef={}, pack={}, montant={} {})",
                payment.getInternalRef(), pack.getId(),
                payment.getFiatAmount(), payment.getFiatCurrency());
        return PaymentResponse.from(payment, initiation);
    }

    /** Détail d'un paiement (propriétaire ou ADMIN — anti IDOR). */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Paiement introuvable"));
        CurrentUser.requireOwnerOrAdmin(actor, payment.getPayer().getId());
        return PaymentResponse.from(payment);
    }

    /** Historique de MES paiements, récents d'abord (paginé). */
    @Transactional(readOnly = true)
    public ApiPageResponse<PaymentResponse> myHistory(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        Page<Payment> page = payments.findByPayerIdOrderByCreatedAtDesc(actor.id(), pageable);
        return ApiPageResponse.from(page,
                page.getContent().stream().map(PaymentResponse::from).toList());
    }

    /** Tous les paiements (ADMIN — supervision, jamais de modification). */
    @Transactional(readOnly = true)
    public ApiPageResponse<PaymentResponse> allPayments(CurrentUser actor, Pageable pageable) {
        CurrentUser.requireAuthenticated(actor);
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Administration réservée");
        }
        Page<Payment> page = payments.findAll(pageable);
        return ApiPageResponse.from(page,
                page.getContent().stream().map(PaymentResponse::from).toList());
    }

    /** Annulation d'un paiement non confirmé (propriétaire ou ADMIN). */
    @Transactional
    public PaymentResponse cancelPayment(Long paymentId, CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Paiement introuvable"));
        CurrentUser.requireOwnerOrAdmin(actor, payment.getPayer().getId());
        payment.cancel();
        log.info("Paiement annulé (internalRef={})", payment.getInternalRef());
        return PaymentResponse.from(payment);
    }

    /**
     * Expiration des abandonnés (scheduler) : INITIATED/PENDING plus vieux que
     * la limite → EXPIRED. Retourne le nombre de bascules (observabilité).
     */
    @Transactional
    public int expireStale(Instant cutoff) {
        List<Payment> stale = payments.findByStatusInAndCreatedAtBefore(
                List.of(Payment.Status.INITIATED, Payment.Status.PENDING), cutoff);
        stale.forEach(Payment::expire);
        if (!stale.isEmpty()) {
            log.info("Expiration : {} paiement(s) abandonné(s) → EXPIRED", stale.size());
        }
        return stale.size();
    }

    /** Même clé réutilisée pour une AUTRE intention = 409 (bug client, pas rejeu). */
    private void checkSameIntention(Payment existing, CurrentUser actor,
                                    CoinPackage pack, CreatePaymentRequest request) {
        if (!existing.getPayer().getId().equals(actor.id())
                || existing.getCoinsCredited() != pack.getCoinsAmount()
                || existing.getProvider() != request.provider()
                || existing.getMethod() != request.method()) {
            throw ApiException.conflict("Clé d'idempotence déjà utilisée pour un autre paiement");
        }
    }

    private static String normalizedKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    /** La clé n'est jamais journalisée en clair (donnée de rejeu). */
    private static String maskKey(String key) {
        if (key.length() <= 8) {
            return "***";
        }
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }
}
