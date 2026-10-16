package com.streamingafrique.backend.payment.provider;

import java.math.BigDecimal;

/**
 * Résultat VÉRIFIÉ d'un webhook prestataire (étape 14).
 *
 * Construit UNIQUEMENT après contrôle de la signature (un webhook à signature
 * invalide ne produit jamais cette valeur : il est rejeté avant). Les montants
 * sont recoupés par le service avec le paiement stocké (montant/devise
 * incorrects → rejet, jamais de crédit).
 */
public record WebhookVerification(
        /** Signature authentique (toujours true ici — sinon rejet en amont). */
        boolean authentic,
        /** Référence prestataire annoncée (peut être NULL selon le prestataire). */
        String providerRef,
        /** Notre référence interne (identifiant primaire du paiement). */
        String internalRef,
        /** Issue annoncée par le prestataire. */
        Outcome outcome,
        /** Montant fiat annoncé (recoupé avec le paiement stocké). */
        BigDecimal fiatAmount,
        /** Devise ISO annoncée (recoupée avec le paiement stocké). */
        String fiatCurrency,
        /** Motif d'échec annoncé (FAILED uniquement, NULL sinon). */
        String failureReason
) {
    /** Issue d'un paiement côté prestataire. */
    public enum Outcome {
        SUCCEEDED, FAILED, PENDING, UNKNOWN
    }
}
