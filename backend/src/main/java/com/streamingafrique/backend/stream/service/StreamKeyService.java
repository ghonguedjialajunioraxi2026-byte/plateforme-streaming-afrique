package com.streamingafrique.backend.stream.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.streamingafrique.backend.stream.config.StreamingProperties;

/**
 * Stream keys (étape 10, PARTIE 3) — primitives cryptographiques pures.
 *
 * Contrat :
 *   - génération : {@link SecureRandom} (jamais {@code Random}, jamais UUID
 *     temporel) + préfixe lisible {@code sa_} ;
 *   - stockage : empreinte SHA-256 hex (64 car.) — la clé en clair ne
 *     persiste JAMAIS, ne transite en log JAMAIS ;
 *   - vérification : comparaison en temps constant (anti timing-oracle).
 *
 * Le cycle de vie (générer / rotation / révocation / résolution ingest)
 * vit dans {@link StreamService} ; ce service ne touche ni base ni réseau.
 */
@Service
public class StreamKeyService {

    /** Préfixe lisible d'une clé (support, copier-coller OBS). */
    public static final String KEY_PREFIX = "sa_";

    private final SecureRandom random = new SecureRandom();
    private final int keyBytes;

    public StreamKeyService(StreamingProperties properties) {
        this.keyBytes = properties.getKeyBytes();
    }

    /** Nouvelle clé en clair (à montrer UNE fois au propriétaire, puis oublier). */
    public String generate() {
        byte[] bytes = new byte[keyBytes];
        random.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Empreinte SHA-256 hexadécimale d'une clé (ce qui est stocké). */
    public String hash(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plainKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
        }
    }

    /**
     * La clé fournie correspond-elle à l'empreinte stockée ?
     * Temps constant (aucune sortie anticipée observable).
     */
    public boolean matches(String plainKey, String storedHash) {
        if (plainKey == null || storedHash == null) {
            return false;
        }
        byte[] expected = storedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(plainKey).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    /** Valeur à saisir dans le champ « clé » d'OBS ({@code <id>?key=<clé>}). */
    public String obsKey(Long streamId, String plainKey) {
        return streamId + "?key=" + plainKey;
    }
}
