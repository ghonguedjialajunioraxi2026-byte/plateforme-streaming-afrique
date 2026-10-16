package com.streamingafrique.backend.auth;

import com.streamingafrique.backend.exception.ApiException;

/**
 * Politique mot de passe (étape 08) — volontairement RAISONNABLE, pas pénible :
 *   - 8 à 100 caractères (le @Size des DTO coupe avant) ;
 *   - au moins UNE lettre ET UN chiffre (pas de caractère spécial imposé :
 *     "MotDePasseFort123!" passe, "motdepasse" et "12345678" non).
 *
 * Centralisée ici (inscription, reset, changement) : UNE règle, TROIS usages.
 * Le hachage BCrypt (coût adaptatif Spring) vit dans le service, jamais ici.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** Vérifie la force ; lève 400 BAD_REQUEST avec un message actionnable sinon. */
    public static void check(String password) {
        if (password == null || !hasLetter(password) || !hasDigit(password)) {
            throw ApiException.badRequest(
                    "Le mot de passe doit contenir au moins une lettre et un chiffre");
        }
    }

    private static boolean hasLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
