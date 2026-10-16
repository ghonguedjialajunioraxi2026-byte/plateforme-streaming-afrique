package com.streamingafrique.backend.ai.provider;

/**
 * Réponse BRUTE d'un provider (traduite en DTO métier par
 * {@code AiService} — jamais exposée telle quelle).
 */
public record AiGeneration(
        /** Texte généré (peut être vide si échec partiel). */
        String text,
        /** Tokens d'entrée si le provider les déclare (sinon null). */
        Integer inputTokens,
        /** Tokens de sortie si déclarés (sinon null). */
        Integer outputTokens,
        /** Le provider a-t-il servi depuis son cache ? */
        boolean cached) {

    public static AiGeneration of(String text) {
        return new AiGeneration(text, null, null, false);
    }
}
