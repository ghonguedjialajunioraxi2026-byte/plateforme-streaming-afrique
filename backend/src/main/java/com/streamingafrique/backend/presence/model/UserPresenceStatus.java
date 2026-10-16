package com.streamingafrique.backend.presence.model;

/**
 * Statut de présence d'un utilisateur.
 *
 * Le système repose sur de l'éphémère (Redis + TTL) : un statut n'est JAMAIS
 * définitif. {@code OFFLINE} est l'état par défaut (absence de marqueur) et
 * {@code ONLINE} signifie « marqueur vivant » (connexion au moins une session).
 *
 * {@code AWAY} est réservé pour plus tard (inactivité constatée côté client,
 * perte de focus, etc.) — le code le transporte mais ne le produit pas encore.
 */
public enum UserPresenceStatus {
    ONLINE,
    OFFLINE,
    AWAY
}