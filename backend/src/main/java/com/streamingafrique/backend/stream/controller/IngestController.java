package com.streamingafrique.backend.stream.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.stream.service.IngestAuthService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Crochet d'ingest du serveur média (étape 23) : {@code on_publish} (SRS).
 *
 * {@code POST /api/ingest/on-publish?secret=…} avec les paramètres du hook
 * ({@code stream}, {@code param}, … en query ou formulaire) — SRS coupe la
 * publication sur tout statut non-2xx. La PREUVE est le secret partagé
 * serveur-à-serveur ({@code INGEST_CALLBACK_SECRET}, jamais dans Git) +
 * statut LIVE + clé de stream (voir {@link IngestAuthService}).
 *
 * Route volontairement hors OpenAPI publique : appel machine-à-machine sur
 * réseau interne uniquement (le reverse proxy ne l'expose pas).
 */
@Hidden
@Tag(name = "Ingest (serveur média)")
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestAuthService ingestAuth;

    public IngestController(IngestAuthService ingestAuth) {
        this.ingestAuth = ingestAuth;
    }

    /**
     * Autorise ou refuse une publication (SRS {@code on_publish}).
     * Succès = 200 {@code {"code":0,"streamId":…}} (protocole SRS : le champ
     * {@code code} DOIT valoir 0, sinon SRS coupe même en 200) ; refus =
     * 401/403/404 (SRS coupe sur tout non-2xx). Pas d'enveloppe
     * {@code ApiResponse} ici : endpoint machine-à-machine.
     *
     * SRS envoie les champs du hook en CORPS JSON
     * ({@code stream}, {@code param}, …), le secret restant en query
     * ({@code ?secret=…}). Les deux sources sont acceptées (autres serveurs
     * média : query/formulaire).
     */
    @PostMapping("/on-publish")
    public ResponseEntity<Map<String, Object>> onPublish(@RequestParam Map<String, String> hook,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        String secret = hook.get("secret");
        if (secret == null && body != null) {
            secret = text(body.get("secret"));
        }
        String stream = hook.get("stream");
        if (stream == null && body != null) {
            stream = text(body.get("stream"));
        }
        String param = hook.get("param");
        if (param == null && body != null) {
            param = text(body.get("param"));
        }
        long streamId = ingestAuth.verify(secret, stream, param);
        return ResponseEntity.ok(Map.of("code", 0, "streamId", streamId));
    }

    private static String text(Object value) {
        return value instanceof String text ? text : null;
    }
}
