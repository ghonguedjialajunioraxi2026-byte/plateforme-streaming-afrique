package com.streamingafrique.backend.ai.provider;

/** Échec d'un fournisseur IA (panne, timeout, réponse malformée…). */
public class AiProviderException extends RuntimeException {

    /** Code machine pour le suivi admin (jamais de secret dedans). */
    private final String code;

    public AiProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AiProviderException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
