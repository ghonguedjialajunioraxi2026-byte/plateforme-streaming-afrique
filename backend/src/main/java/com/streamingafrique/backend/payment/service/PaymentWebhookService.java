package com.streamingafrique.backend.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.payment.config.PaymentProperties;
import com.streamingafrique.backend.payment.model.Payment;
import com.streamingafrique.backend.payment.provider.PaymentProvider;
import com.streamingafrique.backend.payment.provider.PaymentProviderRegistry;
import com.streamingafrique.backend.payment.provider.SandboxPaymentProvider;
import com.streamingafrique.backend.payment.provider.WebhookVerification;
import com.streamingafrique.backend.payment.repository.PaymentRepository;
import com.streamingafrique.backend.security.CurrentUser;

/**
 * Réception des webhooks prestataires (étape 14, §21-23).
 *
 * Pipeline : signature → identification → recoupement montant/devise →
 * transition → crédit ATOMIQUE. Chaque étape de rejet répond sans RIEN
 * persister d'irréversible :
 *   - signature invalide → 401 ;
 *   - paiement inconnu → 404 ;
 *   - montant/devise incorrects → 400 SANS changer l'état (le vrai webhook
 *     pourra encore arriver) ;
 *   - paiement déjà terminal → 200 "déjà traité" (rejeu idempotent) ;
 *   - double webhook simultané → UN seul crédit (clé UNIQUE + verrou wallet ;
 *     le perdant retombe en "déjà traité").
 */
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentProviderRegistry registry;
    private final PaymentRepository payments;
    private final WalletCreditService credits;
    private final PaymentProperties properties;
    private final SandboxPaymentProvider sandbox;

    public PaymentWebhookService(PaymentProviderRegistry registry,
                                 PaymentRepository payments,
                                 WalletCreditService credits,
                                 PaymentProperties properties,
                                 SandboxPaymentProvider sandbox) {
        this.registry = registry;
        this.payments = payments;
        this.credits = credits;
        this.properties = properties;
        this.sandbox = sandbox;
    }

    /**
     * Point d'entrée webhook (contrôleur fin : corps brut + signature).
     *
     * Transaction OBLIGATOIRE ici (et non dans {@code process} seul) :
     * transition + crédit + journal partagent UNE transaction atomique
     * (l'auto-appel interne ne traverse pas le proxy Spring, donc
     * {@code process} seul ne suffirait pas en production).
     */
    @Transactional
    public WebhookOutcome handleWebhook(String providerName, String rawBody, String signature) {        PaymentProvider provider = registry.resolveByName(providerName);
        WebhookVerification verification = provider.verifyWebhook(rawBody, signature);
        try {
            return process(verification);
        } catch (DataIntegrityViolationException concurrent) {
            // Concurrence (double webhook simultané) : le gagnant a committé,
            // le perdant a violé la clé UNIQUE — on retombe en idempotent.
            log.info("Webhook concurrent (internalRef={}) → déjà traité",
                    verification.internalRef());
            return WebhookOutcome.ALREADY_PROCESSED;
        }
    }

    /**
     * Simulateur sandbox (dev/test uniquement) : rejoue LOCALEMENT un webhook
     * signé pour un paiement existant — même pipeline que le vrai webhook
     * (signature, recoupement, crédit atomique). Refusé si le sandbox est
     * désactivé (prod). L'acteur doit être le payeur ou un ADMIN.
     *
     * Transaction OBLIGATOIRE (même raison que {@code handleWebhook} :
     * l'appel interne ne traverse pas le proxy).
     */
    @Transactional
    public WebhookOutcome simulate(String internalRef, WebhookVerification.Outcome outcome,
                                   CurrentUser actor) {
        CurrentUser.requireAuthenticated(actor);
        if (!properties.isSandboxEnabled()) {
            throw ApiException.forbidden("Simulateur indisponible hors sandbox");
        }
        Payment payment = payments.findByInternalRef(internalRef)
                .orElseThrow(() -> ApiException.notFound("Paiement introuvable"));
        CurrentUser.requireOwnerOrAdmin(actor, payment.getPayer().getId());
        if (outcome == null || outcome == WebhookVerification.Outcome.UNKNOWN) {
            throw ApiException.badRequest("Issue simulée invalide (SUCCEEDED, FAILED ou PENDING)");
        }
        String status = switch (outcome) {
            case SUCCEEDED -> "SUCCEEDED";
            case FAILED -> "FAILED";
            default -> "PENDING";
        };
        String rawBody = "{\"internalRef\":\"" + payment.getInternalRef() + "\","
                + "\"providerRef\":\"" + (payment.getProviderRef() == null ? ""
                        + "sbx-simulated" : payment.getProviderRef()) + "\","
                + "\"status\":\"" + status + "\","
                + "\"fiatAmount\":\"" + payment.getFiatAmount().toPlainString() + "\","
                + "\"fiatCurrency\":\"" + payment.getFiatCurrency() + "\""
                + (outcome == WebhookVerification.Outcome.FAILED
                        ? ",\"failureReason\":\"Échec simulé\"" : "")
                + "}";
        log.info("Simulation sandbox (internalRef={}, issue={})", internalRef, status);
        return handleWebhook(payment.getProvider().name(), rawBody, sandbox.sign(rawBody));
    }

    @Transactional
    protected WebhookOutcome process(WebhookVerification verification) {
        Payment payment = payments.findByInternalRef(verification.internalRef())
                .orElseThrow(() -> ApiException.notFound(
                        "Paiement introuvable (internalRef=" + verification.internalRef() + ")"));
        if (isTerminal(payment.getStatus())) {
            log.info("Webhook rejoué (internalRef={}, état={}) — ignoré",
                    payment.getInternalRef(), payment.getStatus());
            return WebhookOutcome.ALREADY_PROCESSED;
        }
        switch (verification.outcome()) {
            case SUCCEEDED -> {
                // Recoupement AVANT tout changement (faux webhook = 400, état intact).
                if (verification.fiatAmount() == null
                        || verification.fiatAmount().compareTo(payment.getFiatAmount()) != 0
                        || !payment.getFiatCurrency().equals(verification.fiatCurrency())) {
                    log.warn("Webhook incohérent (internalRef={}) : montant/devise annoncés "
                                    + "différents du paiement — rejet sans changement d'état",
                            payment.getInternalRef());
                    throw ApiException.badRequest("Montant ou devise incohérents");
                }
                if (verification.providerRef() != null) {
                    payment.attachProviderRef(verification.providerRef());
                }
                payment.confirm();
                boolean credited = credits.creditFor(payment);
                log.info("Paiement confirmé (internalRef={}, crédit={})",
                        payment.getInternalRef(), credited ? "appliqué" : "déjà appliqué");
                return credited ? WebhookOutcome.CONFIRMED : WebhookOutcome.ALREADY_PROCESSED;
            }
            case FAILED -> {
                payment.fail(verification.failureReason() == null
                        ? "Échec signalé par le prestataire" : verification.failureReason());
                log.info("Paiement échoué (internalRef={})", payment.getInternalRef());
                return WebhookOutcome.MARKED_FAILED;
            }
            case PENDING -> {
                if (verification.providerRef() != null) {
                    payment.attachProviderRef(verification.providerRef());
                }
                log.info("Paiement en attente (internalRef={})", payment.getInternalRef());
                return WebhookOutcome.STILL_PENDING;
            }
            default -> {
                log.warn("Webhook d'issue inconnue (internalRef={}) — rejet",
                        payment.getInternalRef());
                throw ApiException.badRequest("Issue de paiement inconnue");
            }
        }
    }

    private static boolean isTerminal(Payment.Status status) {
        return status == Payment.Status.CONFIRMED
                || status == Payment.Status.REFUNDED
                || status == Payment.Status.FAILED
                || status == Payment.Status.CANCELLED
                || status == Payment.Status.EXPIRED;
    }

    /** Issue du traitement (réponse 200 au prestataire sauf erreur). */
    public enum WebhookOutcome {
        CONFIRMED, ALREADY_PROCESSED, MARKED_FAILED, STILL_PENDING
    }
}
