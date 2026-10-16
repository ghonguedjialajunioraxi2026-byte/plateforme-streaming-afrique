package com.streamingafrique.backend.security;

import org.springframework.security.core.Authentication;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.user.model.User;

/**
 * L'utilisateur COURANT d'une requête authentifiée (étape 6.25).
 *
 * Extra de l'{@link Authentication} Spring Security par
 * {@link #from(Authentication)} (principal = {@link AuthenticatedUser}).
 * Porte l'identité (id, email) et le rôle — tout ce qu'un contrôleur doit
 * connaître pour passer l'acteur au service (contrôles de propriété), sans
 * dépendre de l'ordre d'exécution des filtres de sécurité.
 *
 * Les gardes statiques centralisent les règles d'ACCÈS dit « fin » :
 *   - {@link #requireAuthenticated}     : il faut être connecté ;
 *   - {@link #requireOwnerOrAdmin}      : propriétaire de la ressource OU admin ;
 *   - {@link #requireOwnerOrModerator}  : propriétaire OU au moins modérateur ;
 *   - {@link #requireSelf}              : l'acteur ne peut agir que par lui-même.
 * Elles lèvent une 403 métier (ApiException) — la politique de rôles, elle,
 * vit dans les {@code @PreAuthorize} des contrôleurs.
 */
public record CurrentUser(Long id, String email, User.Role role) {

    /** Construit l'acteur courant, ou {@code null} si la requête est anonyme. */
    public static CurrentUser from(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUser au)) {
            return null;
        }
        return new CurrentUser(au.id(), au.email(), au.role());
    }

    /** Fabrique directe (tests unitaires / appels service dédiés). */
    public static CurrentUser of(Long id, String email, User.Role role) {
        return new CurrentUser(id, email, role);
    }

    public boolean isAuthenticated() {
        return id != null;
    }

    /** L'acteur a-t-il au moins {@code required} dans la hiérarchie USER < CREATOR < MODERATOR < ADMIN ? */
    public boolean hasRank(User.Role required) {
        return role != null && role.atLeast(required);
    }

    public boolean isAdmin() {
        return role == User.Role.ADMIN;
    }

    public boolean isModeratorOrAbove() {
        return hasRank(User.Role.MODERATOR);
    }

    /* ─── Gardes d'accès ─────────────────────────────────────────── */

    /** La requête doit être authentifiée (403 sinon). */
    public static void requireAuthenticated(CurrentUser actor) {
        if (actor == null || !actor.isAuthenticated()) {
            throw ApiException.forbidden("Authentification requise pour cette action");
        }
    }

    /** L'acteur doit être le propriétaire de la ressource OU un ADMIN. */
    public static void requireOwnerOrAdmin(CurrentUser actor, Long ownerUserId) {
        requireAuthenticated(actor);
        if (actor.isAdmin()) {
            return;
        }
        if (!actor.id().equals(ownerUserId)) {
            throw ApiException.forbidden("Cette ressource appartient à un autre utilisateur");
        }
    }

    /** L'acteur doit être le propriétaire de la ressource OU au moins MODERATOR. */
    public static void requireOwnerOrModerator(CurrentUser actor, Long ownerUserId) {
        requireAuthenticated(actor);
        if (actor.isModeratorOrAbove()) {
            return;
        }
        if (!actor.id().equals(ownerUserId)) {
            throw ApiException.forbidden("Cette ressource appartient à un autre utilisateur");
        }
    }

    /** L'acteur ne peut agir que POUR LUI-MÊME (ex. suivre, marquer ses notifications lues). */
    public static void requireSelf(CurrentUser actor, Long claimedUserId) {
        requireAuthenticated(actor);
        if (!actor.id().equals(claimedUserId)) {
            throw ApiException.forbidden("Impossible d'agir au nom d'un autre utilisateur");
        }
    }
}