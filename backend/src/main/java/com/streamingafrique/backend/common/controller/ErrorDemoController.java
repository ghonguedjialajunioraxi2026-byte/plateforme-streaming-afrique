package com.streamingafrique.backend.common.controller;

import com.streamingafrique.backend.common.dto.DemoRegistrationRequest;
import com.streamingafrique.backend.common.dto.DemoRequest;
import com.streamingafrique.backend.exception.ApiException;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints DE TEST uniquement : ils servent à déclencher chaque type
 * d'erreur et à vérifier la réponse JSON uniforme (ApiError).
 *
 * ⚠️ À SUPPRIMER avant mise en production.
 */
@Tag(name = "Démo (erreurs)", description = "Routes DE TEST (PUBLIC) : déclenchent "
        + "chaque forme d'erreur pour visualiser l'enveloppe `ApiError`. "
        + "Ne pas utiliser dans le frontend — à supprimer avant production.")
@RestController
@RequestMapping("/api/test/errors")
public class ErrorDemoController {

    /** 404 — exception métier NOT_FOUND. */
    @Operation(summary = "[DÉMO] Erreur 404", description = "Renvoie `NOT_FOUND` en JSON uniforme.")
    @GetMapping("/not-found")
    public void notFound() {
        throw ApiException.notFound("Ressource introuvable");
    }

    /** 409 — exception métier CONFLICT. */
    @Operation(summary = "[DÉMO] Erreur 409", description = "Renvoie `CONFLICT` en JSON uniforme.")
    @GetMapping("/conflict")
    public void conflict() {
        throw ApiException.conflict("Conflit : cette donnée existe déjà");
    }

    /** 400 — exception métier BAD_REQUEST. */
    @Operation(summary = "[DÉMO] Erreur 400", description = "Renvoie `BAD_REQUEST` en JSON uniforme.")
    @GetMapping("/bad-request")
    public void badRequest() {
        throw ApiException.badRequest("Requête invalide");
    }

    /** 403 — exception métier FORBIDDEN. */
    @Operation(summary = "[DÉMO] Erreur 403", description = "Renvoie `FORBIDDEN` en JSON uniforme.")
    @GetMapping("/forbidden")
    public void forbidden() {
        throw ApiException.forbidden("Accès refusé");
    }

    /**
     * 400 — validation d'un DTO (@Valid).
     * Test : POST /api/test/errors/validation avec un corps vide {}
     */
    @Operation(summary = "[DÉMO] Erreur de validation",
            description = "Envoyer `{}` → `400 VALIDATION_ERROR` avec le détail par champ (`errors`).")
    @PostMapping("/validation")
    public void validation(@Valid @RequestBody DemoRequest request) {
        // Jamais atteint si le corps est invalide : la validation échoue avant.
    }

    /**
     * 400 — validation complète (toutes annotations).
     * Test : POST /api/test/errors/register avec un corps invalide.
     */
    @Operation(summary = "[DÉMO] Validation complète",
            description = "Toutes les annotations de validation sur un même DTO "
                    + "(`DemoRegistrationRequest`).")
    @PostMapping("/register")
    public void register(@Valid @RequestBody DemoRegistrationRequest request) {
        // Jamais atteint si le corps est invalide : la validation échoue avant.
    }

    /**
     * 400 — mauvais type de paramètre.
     * Test : GET /api/test/errors/bad-param/abc  (id est un Long)
     */
    @Operation(summary = "[DÉMO] Mauvais type de paramètre",
            description = "Ex. `/api/test/errors/bad-param/abc` → `400 BAD_PARAM`.")
    @GetMapping("/bad-param/{id}")
    public void badParam(@PathVariable long id) {
        // Corps vide volontaire : le but est d'atteindre la conversion du paramètre.
    }

    /**
     * 400 — paramètre requis manquant.
     * Test : GET /api/test/errors/missing-param  (sans ?q=...)
     */
    @Operation(summary = "[DÉMO] Paramètre requis manquant",
            description = "Sans `?q=...` → `400 MISSING_PARAM`.")
    @GetMapping("/missing-param")
    public void missingParam(@RequestParam String q) {
        // Corps vide volontaire : le but est le paramètre requis manquant.
    }

    /**
     * 500 — erreur interne imprévue.
     * La trace complète va dans les logs ; le client ne reçoit qu'un message générique.
     */
    @Operation(summary = "[DÉMO] Erreur interne 500",
            description = "Le client reçoit `500 INTERNAL_ERROR` générique ; "
                    + "la pile reste côté serveur.")
    @GetMapping("/internal")
    public void internal() {
        throw new IllegalStateException("Erreur simulée pour le test");
    }
}