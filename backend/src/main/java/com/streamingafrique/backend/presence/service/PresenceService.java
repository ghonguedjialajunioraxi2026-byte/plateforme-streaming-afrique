package com.streamingafrique.backend.presence.service;

import com.streamingafrique.backend.presence.dto.UserPresenceDto;
import com.streamingafrique.backend.presence.model.UserPresenceStatus;

import java.util.List;

/**
 * Service de présence utilisateur.
 *
 * Le socle qui permettra plus tard de savoir qui est :
 *   - en ligne ({@code ONLINE}) ;
 *   - hors ligne ({@code OFFLINE}) ;
 *   - éventuellement absent ({@code AWAY}, réservé pour plus tard).
 *
 * Éphémère par construction : une présence est un « marqueur » qui vit dans
 * Redis avec un TTL et doit être prolongé (hearbeat) pour rester valide.
 * Aucune présence n'est donc jamais considérée comme définitive.
 */
public interface PresenceService {

    /** Une nouvelle session WebSocket s'ouvre pour l'utilisateur (compteur + TTL). */
    void markOnline(Long userId);

    /** Une session WebSocket se ferme ; la dernière fermeture → OFFLINE. */
    void markOffline(Long userId);

    /** Prolonge le TTL + actualise lastSeenAt d'un utilisateur actif (heartbeat). */
    void touch(Long userId);

    /** Présence courante (jamais null : OFFLINE si aucun marqueur vivant). */
    UserPresenceDto userPresence(Long userId);

    /** Statut courant (OFFLINE par défaut). */
    UserPresenceStatus statusOf(Long userId);

    /** Vivant (marqueur présent et NON expiré) ? */
    boolean isOnline(Long userId);

    /** Nombre d'utilisateurs en ligne (marqueurs vivants). */
    long countOnline();

    /** Identifiants des utilisateurs en ligne. */
    List<Long> onlineUserIds();

    /**
     * Balayage des présences obsolètes : retire de l'ensemble « online » les
     * utilisateurs dont le marqueur a expiré ou est resté figé au-delà du TTL
     * (session disparue sans déconnexion propre) et diffuse leur passage OFFLINE.
     *
     * @return nombre de présences nettoyées
     */
    long sweep();
}