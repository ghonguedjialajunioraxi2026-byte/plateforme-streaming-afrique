package com.streamingafrique.backend.payment.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.streamingafrique.backend.common.model.BaseEntity;
import com.streamingafrique.backend.user.model.User;

/**
 * Entité "Payment" → table {@code payments}.
 *
 * RÔLE : l'achat de coins en monnaie fiat via un prestataire externe
 * (sous-étape 7.22) — Mobile Money (Wave, Orange Money, MTN MoMo, M-Pesa),
 * carte bancaire, virement, prestataires internationaux (Stripe, Paystack,
 * Flutterwave). AUCUNE intégration codée ici : que la structure, prête
 * pour plusieurs fournisseurs (couple méthode + prestataire extensible).
 *
 * Anti-fraude / idempotence : {@code internalRef} UNIQUE générée (nos retries),
 * {@code providerRef} UNIQUE (rejeu webhook). Remboursement = lignes de
 * {@link PaymentRefund}, jamais d'UPDATE destructeur (statut REFUNDED =
 * état, pas effacement).
 *
 * INTERDIT (7.22) : PAN, CVV, numéro complet de carte ou de mobile money —
 * seul un MASQUE partiel ({@code accountMasked}, ex. "+221 77 *** 123")
 * est conservé pour le support. Aucun champ sensible n'existe dans cette
 * entité (vérifié par test réflexif).
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "uk_payments_internal_ref", columnList = "internalRef", unique = true),
        @Index(name = "uk_payments_provider_ref", columnList = "providerRef", unique = true),
        @Index(name = "idx_payments_payer_status", columnList = "payer_id,status")
})
public class Payment extends BaseEntity {

    /** Canal de paiement (extensible sans migration de code métier). */
    public enum Method {
        MOBILE_MONEY, CARD, BANK_TRANSFER, OTHER
    }

    /** Prestataire (extensible : ajouter la valeur, pas de table). */
    public enum Provider {
        WAVE, ORANGE_MONEY, MTN_MOMO, MPESA, STRIPE, PAYSTACK, FLUTTERWAVE, OTHER
    }

    /** Cycle : initié → confirmé / échoué / annulé / expiré ; confirmé → remboursé. */
    public enum Status {
        INITIATED, PENDING, CONFIRMED, FAILED, CANCELLED, EXPIRED, REFUNDED
    }

    /** Le payeur — jamais null. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    /** Référence interne unique (nos retries, notre compta). */
    @Column(nullable = false, unique = true, length = 100)
    private String internalRef;

    /** Référence prestataire (rejeu webhook) — NULL tant que non attribuée. */
    @Column(length = 200)
    private String providerRef;

    /**
     * Clé d'idempotence de la CRÉATION (étape 14) : le client génère un UUID
     * par intention d'achat et le renvoie à chaque retry — deux requêtes avec
     * la même clé ne créent JAMAIS deux paiements (UNIQUE, NULL = absence de
     * clé : l'unicité SQL ignore les NULL, comportement identique PG/H2).
     */
    @Column(length = 100, unique = true)
    private String idempotencyKey;

    /** Montant fiat débité (strictement positif). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fiatAmount;

    /** Devise ISO (ex. "XOF", "EUR") — jamais null. */
    @Column(nullable = false, length = 3)
    private String fiatCurrency;

    /** Coins crédités en contrepartie (strictement positif). */
    @Column(nullable = false)
    private long coinsCredited;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /** État par défaut : initié. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.INITIATED;

    /** Compte masqué pour le support (ex. "+221 77 *** 123") — jamais complet. */
    @Column(length = 50)
    private String accountMasked;

    /**
     * Métadonnées NON sensibles (JSON : identifiant de commande locale,
     * pays, canal détaillé…). Jamais de PAN/CVV/tokens bruts.
     */
    @Column(length = 2000)
    private String metadata;

    /** Motif d'échec / d'annulation — NULL si confirmé. */
    @Column(length = 500)
    private String failureReason;

    /** Constructeur vide requis par JPA (ne pas utiliser autrement). */
    protected Payment() {
    }

    /** Fabrique : montants positifs, devise 3 lettres, référence générée. */
    public static Payment initiate(User payer, BigDecimal fiatAmount, String fiatCurrency,
                                   long coinsCredited, Method method, Provider provider,
                                   String accountMasked, String metadata) {
        return initiate(payer, fiatAmount, fiatCurrency, coinsCredited, method, provider,
                accountMasked, metadata, null);
    }

    /**
     * Fabrique avec clé d'idempotence (étape 14) : même clé + même payeur +
     * même pack = même intention (le service retrouve l'existant au lieu de
     * créer un doublon). Clé null/blank = intention non rejouable explicitement.
     */
    public static Payment initiate(User payer, BigDecimal fiatAmount, String fiatCurrency,
                                   long coinsCredited, Method method, Provider provider,
                                   String accountMasked, String metadata, String idempotencyKey) {
        if (payer == null || method == null || provider == null) {
            throw new IllegalArgumentException("Payeur, méthode et prestataire sont obligatoires");
        }
        if (fiatAmount == null || fiatAmount.signum() <= 0 || coinsCredited <= 0) {
            throw new IllegalArgumentException("Montant fiat et coins strictement positifs exigés");
        }
        if (fiatCurrency == null || !fiatCurrency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Devise ISO à 3 lettres exigée");
        }
        Payment payment = new Payment();
        payment.payer = payer;
        payment.internalRef = "pay-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        payment.fiatAmount = fiatAmount;
        payment.fiatCurrency = fiatCurrency;
        payment.coinsCredited = coinsCredited;
        payment.method = method;
        payment.provider = provider;
        payment.accountMasked = accountMasked;
        payment.metadata = metadata;
        payment.status = Status.INITIATED;
        payment.idempotencyKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? null : idempotencyKey.trim();
        return payment;
    }

    /** Référence prestataire reçue (webhook / redirection retour). */
    public void attachProviderRef(String providerRef) {
        this.providerRef = providerRef;
        if (this.status == Status.INITIATED) {
            this.status = Status.PENDING;
        }
    }

    /** Confirmation (INITIATED/PENDING → CONFIRMED). */
    public void confirm() {
        if (this.status == Status.INITIATED || this.status == Status.PENDING) {
            this.status = Status.CONFIRMED;
        }
    }

    /** Échec (motif obligatoire). */
    public void fail(String failureReason) {
        if (this.status == Status.INITIATED || this.status == Status.PENDING) {
            if (failureReason == null || failureReason.trim().isEmpty()) {
                throw new IllegalArgumentException("Le motif d'échec est obligatoire");
            }
            this.status = Status.FAILED;
            this.failureReason = failureReason;
        }
    }

    /** Annulation avant confirmation. */
    public void cancel() {
        if (this.status == Status.INITIATED || this.status == Status.PENDING) {
            this.status = Status.CANCELLED;
        }
    }

    /**
     * Expiration (étape 14) : un paiement resté INITIATED/PENDING au-delà du
     * délai fournisseur bascule en EXPIRED via le scheduler — jamais crédité,
     * jamais rejouable vers CONFIRMED (les transitions ne partent que de
     * INITIATED/PENDING).
     */
    public void expire() {
        if (this.status == Status.INITIATED || this.status == Status.PENDING) {
            this.status = Status.EXPIRED;
        }
    }

    /** Remboursement constaté (CONFIRMED → REFUNDED, lignes PaymentRefund à part). */
    public void markRefunded() {
        if (this.status == Status.CONFIRMED) {
            this.status = Status.REFUNDED;
        }
    }

    public User getPayer() {
        return payer;
    }

    public String getInternalRef() {
        return internalRef;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getFiatAmount() {
        return fiatAmount;
    }

    public String getFiatCurrency() {
        return fiatCurrency;
    }

    public long getCoinsCredited() {
        return coinsCredited;
    }

    public Method getMethod() {
        return method;
    }

    public Provider getProvider() {
        return provider;
    }

    public Status getStatus() {
        return status;
    }

    public String getAccountMasked() {
        return accountMasked;
    }

    public String getMetadata() {
        return metadata;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
