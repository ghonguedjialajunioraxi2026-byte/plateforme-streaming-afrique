package com.streamingafrique.backend.payment.dto;

import com.streamingafrique.backend.payment.model.Payment;

import jakarta.validation.constraints.NotNull;

/**
 * Création d'un paiement (étape 14, §16).
 *
 * Le frontend n'envoie QUE l'intention ({@code packageId} + prestataire +
 * moyen + clé d'idempotence). Prix, coins, devise, payeur et statut sont
 * déterminés CÔTÉ SERVEUR (§15, §17) — tout champ {@code price/coins/
 * currency/userId/status} éventuellement envoyé est IGNORÉ (aucun champ de
 * ce type n'existe ici : impossible même de le fournir).
 */
public record CreatePaymentRequest(
        /** Pack choisi (prix/coins relus en base). */
        @NotNull(message = "Le pack est obligatoire")
        Long packageId,

        /** Prestataire (WAVE, ORANGE_MONEY, MTN_MOMO, MPESA, STRIPE, PAYSTACK, FLUTTERWAVE, OTHER). */
        @NotNull(message = "Le prestataire est obligatoire")
        Payment.Provider provider,

        /** Moyen (MOBILE_MONEY, CARD, BANK_TRANSFER, OTHER). */
        @NotNull(message = "Le moyen de paiement est obligatoire")
        Payment.Method method,

        /**
         * Clé d'idempotence générée par le client (UUID par intention d'achat).
         * Retry avec la même clé = même paiement (jamais de doublon). NULL =
         * intention non rejouable explicitement (chaque appel crée un paiement).
         */
        String idempotencyKey
) {
}
