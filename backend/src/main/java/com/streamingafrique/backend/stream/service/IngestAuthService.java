package com.streamingafrique.backend.stream.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamingafrique.backend.exception.ApiException;
import com.streamingafrique.backend.stream.config.StreamingProperties;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.repository.StreamRepository;

/**
 * Authentification d'ingest du serveur média (étape 23) — garde du hook
 * {@code on_publish} (SRS) : {@code POST /api/ingest/on-publish}.
 *
 * Le serveur média ne fait pas foi : chaque publication RTMP est revalidée
 * ici AVANT acceptation, dans l'ordre :
 *   1. secret partagé {@code ?secret=} (preuve serveur-à-serveur, même
 *      motif que les webhooks HMAC de paiement) — comparaison en temps
 *      constant, sans le secret la réponse est 401 et SRS coupe la
 *      publication ;
 *   2. direct existant (404 sinon) ;
 *   3. direct au statut LIVE (démarré via l'API par le propriétaire — 403
 *      sinon : pas de squat d'un direct programmé ou terminé) ;
 *   4. clé de stream : si une empreinte est attachée au direct, la clé
 *      fournie doit correspondre ({@link StreamKeyService#matches}, temps
 *      constant — 403 sinon). Sans empreinte attachée (émission de clé non
 *      encore câblée côté produit), le secret partagé + statut LIVE restent
 *      la preuve — un avertissement est journalisé.
 *
 * Aucune donnée sensible en log : ni secret partagé, ni clé en clair.
 */
@Service
public class IngestAuthService {

    private static final Logger log = LoggerFactory.getLogger(IngestAuthService.class);

    private final StreamRepository repository;
    private final StreamKeyService keyService;
    private final StreamingProperties properties;

    public IngestAuthService(StreamRepository repository,
                             StreamKeyService keyService,
                             StreamingProperties properties) {
        this.repository = repository;
        this.keyService = keyService;
        this.properties = properties;
    }

    /** Vérifie une publication et renvoie l'identifiant du direct autorisé. */
    @Transactional(readOnly = true)
    public long verify(String secret, String stream, String param) {
        if (!constantTimeEquals(secret, properties.getIngestSecret())) {
            throw ApiException.unauthorized("Secret ingest invalide");
        }
        ParsedTarget target = parse(stream, param);
        Stream live = repository.findById(target.streamId)
                .orElseThrow(() -> ApiException.notFound("Direct introuvable (id=" + target.streamId + ")"));
        if (live.getStatus() != Stream.Status.LIVE) {
            throw ApiException.forbidden("Publication refusée : direct non démarré (statut "
                    + live.getStatus() + ")");
        }
        String storedHash = live.getStreamKeyHash();
        if (storedHash != null && !storedHash.isBlank()) {
            if (target.plainKey == null || !keyService.matches(target.plainKey, storedHash)) {
                throw ApiException.forbidden("Clé de stream invalide");
            }
        } else {
            log.warn("Ingest autorisé par secret partagé seul (direct {}, aucune clé attachée)",
                    live.getId());
        }
        log.info("Ingest autorisé (direct {})", live.getId());
        return live.getId();
    }

    /** Cible extraite de {@code stream} / {@code param} (SRS : {@code 42} + {@code ?key=...}). */
    private record ParsedTarget(long streamId, String plainKey) {
    }

    private ParsedTarget parse(String stream, String param) {
        String combined = (stream == null ? "" : stream.trim())
                + (param == null || param.isBlank() ? "" : (param.startsWith("?") ? "" : "?") + param.trim());
        // Clé éventuelle : "...?key=<clé>" ou "...&key=<clé>".
        String plainKey = null;
        String head = combined;
        int keyAt = indexOfKeyParam(combined);
        if (keyAt >= 0) {
            head = combined.substring(0, keyAt - 1);
            // keyAt pointe sur "key=…" : on saute le préfixe "key=".
            String tail = combined.substring(keyAt + "key=".length());
            int amp = tail.indexOf('&');
            plainKey = amp < 0 ? tail : tail.substring(0, amp);
            if (plainKey.isBlank()) {
                plainKey = null;
            }
        }
        // Identifiant : chiffres en tête ("42", "42?key=…", "live/42").
        String digits = head.replaceAll(".*?(\\d+).*", "$1");
        long streamId;
        try {
            streamId = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Nom de stream ingest invalide");
        }
        return new ParsedTarget(streamId, plainKey);
    }

    private static int indexOfKeyParam(String combined) {
        int q = combined.indexOf("?key=");
        int a = combined.indexOf("&key=");
        if (q < 0) {
            return a < 0 ? -1 : a + 1;
        }
        if (a < 0) {
            return q + 1;
        }
        return Math.min(q, a) + 1;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a == null ? new byte[0] : a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b == null ? new byte[0] : b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(x, y);
    }
}
