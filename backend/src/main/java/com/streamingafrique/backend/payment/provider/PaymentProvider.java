package com.streamingafrique.backend.payment.provider;

import java.util.Set;

import com.streamingafrique.backend.payment.model.Payment;

/**
 * Abstraction d'un prestataire de paiement (étape 14, §7).
 *
 * Ajouter un vrai fournisseur (Wave, Orange Money, MTN MoMo, Stripe…) =
 * implémenter cette interface (création, vérification de signature, lecture
 * de statut) et l'enregistrer dans le registre — JAMAIS réécrire le système
 * (création, webhook, crédit wallet restent inchangés).
 *
 * Règle §70 : ne pas inventer d'API prestataire. Tant qu'un fournisseur réel
 * (documentation officielle, sandbox, webhooks, signature) n'est pas câblé,
 * le registre route vers le sandbox documenté.
 */
public interface PaymentProvider {

    /** Clé d'enregistrement (ex. "SANDBOX", "WAVE"). */
    String key();

    /** Moyens supportés par ce prestataire. */
    Set<Payment.Method> supportedMethods();

    /**
     * Initialise le paiement chez le prestataire (montants LUS depuis
     * l'entité — jamais depuis le frontend). Ne crédite RIEN.
     */
    ProviderInitiation initiate(Payment payment);

    /**
     * Vérifie l'authenticité d'un webhook (signature HMAC selon la
     * documentation du prestataire) et l'interprète. Lève
     * {@link com.streamingafrique.backend.exception.ApiException} (401/400)
     * si la signature est invalide ou le corps illisible/incomplet.
     *
     * @param rawBody corps brut reçu (pour le calcul HMAC)
     * @param signature valeur de l'en-tête de signature (peut être NULL)
     */
    WebhookVerification verifyWebhook(String rawBody, String signature);
}
