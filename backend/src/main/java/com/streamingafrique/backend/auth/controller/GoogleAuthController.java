package com.streamingafrique.backend.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.auth.dto.GoogleIdTokenRequest;
import com.streamingafrique.backend.auth.dto.LoginResponse;
import com.streamingafrique.backend.auth.dto.MeResponse;
import com.streamingafrique.backend.auth.google.GoogleAuthService;
import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Connexion "Continuer avec Google" (flux ID token OIDC, sans callback).
 *
 * Pourquoi PAS de {@code GET /api/auth/google/callback} : en flux ID token,
 * Google remet l'id_token DIRECTEMENT au frontend (Google Identity
 * Services) — il n'y a ni redirect_uri backend, ni {@code state}, ni échange
 * code/secret. Le frontend POSTe ce token ici, le backend le VÉRIFIE
 * (signature, iss, aud, exp) puis émet le JWT FLICK habituel. Le Client
 * Secret Google n'existe donc nulle part dans ce flux (ni frontend, ni Git).
 *
 *   - POST /api/auth/google (PUBLIC) : id_token vérifié → compte trouvé ou
 *     créé (CAS 1/2) → paire JWT FLICK (même contrat que /login) ;
 *   - POST /api/auth/google/link (AUTHENTIFIÉ) : lie le compte Google au
 *     compte connecté (CAS 3 résolu par l'utilisateur lui-même, après login
 *     mot de passe — jamais de fusion automatique sur simple email).
 */
@Tag(name = "Authentification Google", description = "Connexion et liaison via "
        + "Google Identity Services (id_token vérifié côté serveur, JWT FLICK remis).")
@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;

    public GoogleAuthController(GoogleAuthService googleAuthService) {
        this.googleAuthService = googleAuthService;
    }

    @Operation(summary = "Se connecter avec Google",
            description = "Vérifie l'id_token Google (signature, émetteur, audience, "
                    + "expiration) puis connecte ou crée le compte FLICK et remet la "
                    + "paire access + refresh (même contrat que /login). Email LOCAL "
                    + "existant → 409 (connectez-vous puis liez depuis les paramètres).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Connecté : même `data` que /login"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Session Google invalide ou expirée → `UNAUTHORIZED`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Compte suspendu ou fermé → `FORBIDDEN`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Email déjà pris par un compte LOCAL → `CONFLICT` "
                            + "(aucun doublon créé, aucune fusion automatique)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "Trop de tentatives (voir `Retry-After`)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleIdTokenRequest request,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.success("Connexion Google réussie",
                googleAuthService.loginWithGoogle(request.idToken(),
                        header(httpRequest, "User-Agent"), httpRequest.getRemoteAddr()));
    }

    @Operation(summary = "Lier son compte Google (connecté)",
            description = "Lie le compte Google (id_token vérifié) au compte FLICK "
                    + "connecté (Bearer requis — c'est cette session qui prouve "
                    + "l'identité). 409 si ce Google est déjà lié à un autre compte.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Compte lié : `data` = profil courant"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié ou session Google invalide",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Ce Google est déjà lié à un autre compte",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/link")
    public ApiResponse<MeResponse> linkGoogle(@Valid @RequestBody GoogleIdTokenRequest request,
                                              Authentication authentication) {
        return ApiResponse.success("Compte Google lié",
                googleAuthService.linkGoogle(CurrentUser.from(authentication), request.idToken()));
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : value;
    }
}
