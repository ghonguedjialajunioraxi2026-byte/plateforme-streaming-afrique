package com.streamingafrique.backend.dto;

import java.time.Instant;

/**
 * Enveloppe standard de TOUTES les réponses de succès de l'API.
 *
 * Contrat :
 *   success   → toujours {@code true} pour un succès
 *   message   → optionnel (text explicatif, supprimé si null)
 *   data      → le vrai contenu (DTO, jamais une entité JPA)
 *   timestamp → horodatage UTC de la réponse
 *
 * Les données publiques sont TOUJOURS des DTO : les entités JPA
 * restent strictement internes (elles exposeraient trop d'informations).
 *
 * @param <T> type de la donnée transportée
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {

    /** Succès sans message : { success, data, timestamp }. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, Instant.now());
    }

    /** Succès avec message explicatif + donnée. */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }
}