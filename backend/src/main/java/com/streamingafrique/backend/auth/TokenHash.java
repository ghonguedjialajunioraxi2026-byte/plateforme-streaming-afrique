package com.streamingafrique.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Empreinte SHA-256 hexadécimale des tokens persistés (étape 08).
 *
 * Les refresh JWT et les jetons email ne sont JAMAIS stockés en brut :
 * seule l'empreinte vit en base (auth_sessions.token_hash,
 * email_tokens.token_hash). Une fuite base ne permet donc pas de rejouer
 * les tokens — même logique que les mots de passe (hashés, jamais en clair),
 * avec SHA-256 (comparaison exacte, pas de sel nécessaire : les tokens ont
 * 256 bits d'entropie).
 */
public final class TokenHash {

    private TokenHash() {
    }

    /** Empreinte SHA-256 hexadécimale (64 caractères) d'un token brut. */
    public static String sha256Hex(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
