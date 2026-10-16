package com.streamingafrique.backend.ai.provider;

/**
 * ABSTRACTION fournisseur IA (étape 18) — l'application ne dépend
 * QUE de cette interface. Changer de modèle (OpenAI ↔ Gemini ↔
 * Ollama local…) = nouvelle implémentation, zéro réécriture métier.
 */
public interface AiProvider {

    /** Nom du provider actif (ex. "stub", "http"). */
    String name();

    /**
     * Génère un texte. Lève {@link AiProviderException} si le
     * fournisseur est indisponible, trop lent ou répond mal.
     */
    AiGeneration generate(AiPrompt prompt) throws AiProviderException;
}
