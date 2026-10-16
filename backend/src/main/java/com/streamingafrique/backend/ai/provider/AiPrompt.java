package com.streamingafrique.backend.ai.provider;

/**
 * Prompt INTERNE transmis au provider (déjà nettoyé par
 * {@code AiService} : secrets retirés, taille bornée, données
 * utilisateur marquées comme DONNÉES — jamais comme instructions).
 */
public record AiPrompt(
        /** Consigne système (rôle, langue, garde-fous). */
        String system,
        /** Contenu utilisateur (donnée non fiable). */
        String user,
        /** Plafond de sortie (tokens/mots selon provider). */
        int maxOutputTokens,
        /** Langue de réponse : "fr" ou "en". */
        String language) {
}
