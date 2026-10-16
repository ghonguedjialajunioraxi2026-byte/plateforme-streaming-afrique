package com.streamingafrique.backend.dto;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Corps d'erreur renvoyé par le gestionnaire global d'exceptions.
 * Toujours accompagné du code HTTP correct dans l'en-tête.
 *
 * Contrat :
 *   success   → toujours {@code false}
 *   status    → code HTTP numérique (400, 404, 500...)
 *   errorCode → code machine ("NOT_FOUND", "VALIDATION_ERROR"...)
 *   message   → texte lisible par l'utilisateur
 *   errors    → optionnel : map "champ invalide" → "message" (validation)
 *   path      → route appelée ("/api/...") pour faciliter le débogage
 *   timestamp → horodatage UTC de l'erreur
 */
public record ApiError(
        boolean success,
        int status,
        String errorCode,
        String message,
        Map<String, String> errors,
        String path,
        Instant timestamp
) {

    /** Erreur classique, sans détail de champ. */
    public static ApiError of(HttpStatus status, String errorCode, String message, String path) {
        return new ApiError(false, status.value(), errorCode, message, null, path, Instant.now());
    }

    /** Erreur de validation : liste des champs invalides et leur message. */
    public static ApiError ofValidation(HttpStatus status, String errorCode, String message,
                                        Map<String, String> errors, String path) {
        return new ApiError(false, status.value(), errorCode, message, errors, path, Instant.now());
    }
}