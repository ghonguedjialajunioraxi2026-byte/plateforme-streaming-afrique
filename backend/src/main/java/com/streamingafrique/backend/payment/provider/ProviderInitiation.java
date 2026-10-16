package com.streamingafrique.backend.payment.provider;

import java.time.Instant;

/**
 * Résultat de l'initialisation d'un paiement chez un prestataire (étape 14).
 *
 * Selon le prestataire, le frontend doit ensuite :
 *   - REDIRECT : naviguer vers {@code actionUrl} (page hébergée du prestataire) ;
 *   - INSTRUCTIONS : afficher {@code instructions} (ex. composer un code USSD,
 *     valider une demande sur le téléphone) puis sonder le statut ;
 *   - NONE : rien à faire côté client (prélèvement immédiat confirmé par webhook).
 */
public record ProviderInitiation(
        /** Référence attribuée par le prestataire (NULL si non attribuée). */
        String providerRef,
        /** Ce que le frontend doit présenter à l'utilisateur. */
        ActionType actionType,
        /** URL de redirection (REDIRECT uniquement, NULL sinon). */
        String actionUrl,
        /** Consigne à afficher (INSTRUCTIONS uniquement, NULL sinon). */
        String instructions,
        /** Échéance de l'action (NULL si inconnue). */
        Instant expiresAt
) {
    /** Nature de l'action de paiement à présenter. */
    public enum ActionType {
        REDIRECT, INSTRUCTIONS, NONE
    }
}
