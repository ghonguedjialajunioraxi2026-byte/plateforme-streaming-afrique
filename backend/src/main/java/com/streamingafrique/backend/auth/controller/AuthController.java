package com.streamingafrique.backend.auth.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.auth.dto.AuthTokens;
import com.streamingafrique.backend.auth.dto.ChangePasswordRequest;
import com.streamingafrique.backend.auth.dto.ForgotPasswordRequest;
import com.streamingafrique.backend.auth.dto.LoginRequest;
import com.streamingafrique.backend.auth.dto.LoginResponse;
import com.streamingafrique.backend.auth.dto.MeResponse;
import com.streamingafrique.backend.auth.dto.RefreshRequest;
import com.streamingafrique.backend.auth.dto.RegisterRequest;
import com.streamingafrique.backend.auth.dto.ResendVerificationRequest;
import com.streamingafrique.backend.auth.dto.ResetPasswordRequest;
import com.streamingafrique.backend.auth.dto.SessionDto;
import com.streamingafrique.backend.auth.dto.VerifyEmailRequest;
import com.streamingafrique.backend.auth.service.AuthService;
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
 * Authentification complète (étapes 6.24 + 08).
 *
 * Routes PUBLIQUES : register, login, refresh, verify-email, resend-verification,
 * forgot-password, reset-password. Routes AUTHENTIFIÉES : me, logout, sessions,
 * change-password. Aucune logique ici : validation @Valid + délégation au
 * {@link AuthService}. Toutes les réponses d'erreur suivent le contrat
 * {@code ApiError} (jamais de pile, jamais de secret).
 */
@Tag(name = "Authentification", description = "Inscription, connexion JWT (access + "
        + "refresh avec rotation), logout, compte courant, sessions, vérification email, "
        + "récupération et changement de mot de passe.")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "S'inscrire",
            description = "Crée le compte (rôle USER forcé serveur), son profil et un "
                    + "jeton de vérification email (24 h). 409 si email/pseudo pris.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Compte créé : `data` = `{id, username, email, displayName, role, emailVerified:false}`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR` (format) ou `BAD_REQUEST` (confirmation, force)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Email ou pseudo déjà pris → `CONFLICT`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MeResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("Compte créé, vérifiez votre email", authService.register(request));
    }

    @Operation(summary = "Se connecter",
            description = "Vérifie email + mot de passe et remet paire access (court) + "
                    + "refresh (7 j, session persistée). L'email est insensible à la "
                    + "casse. En cas d'échec, la réponse ne révèle jamais si c'est l'email "
                    + "ou le mot de passe qui est en cause.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Connecté : `data` = "
                    + "`{accessToken, tokenType:\"Bearer\", expiresIn, userId, email, displayName, role,"
                    + " refreshToken, refreshExpiresIn}`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Requête invalide",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Identifiants incorrects → "
                    + "`INVALID_CREDENTIALS` (limité anti force-brute : 429 au-delà du quota)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Trop de tentatives (voir `Retry-After`)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        // Les infos appareil/IP de la session sont lues côté service si besoin futur ;
        // la signature actuelle reste compatible (device tracé au refresh).
        return ApiResponse.success("Connexion réussie", authService.login(request));
    }

    @Operation(summary = "Renouveler les jetons",
            description = "Refresh valide + session active → ROTATION : l'ancien refresh "
                    + "est révoqué, une nouvelle paire est remise. Refresh inconnu, "
                    + "révoqué, expiré ou falsifié → 400 (pas d'info exploitable).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Nouvelle paire `{accessToken, tokenType, expiresIn, refreshToken, refreshExpiresIn}`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`BAD_REQUEST` (refresh invalide/expiré/révoqué)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/refresh")
    public ApiResponse<AuthTokens> refresh(@Valid @RequestBody RefreshRequest request,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.success("Jetons renouvelés",
                authService.refresh(request.refreshToken(), header(httpRequest, "User-Agent"),
                        httpRequest.getRemoteAddr()));
    }

    @Operation(summary = "Se déconnecter",
            description = "Révoque la session du refresh fourni. Idempotent : succès "
                    + "même si le token est inconnu ou déjà révoqué (aucune info fuitée).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Session révoquée")
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success("Déconnexion réussie", null);
    }

    @Operation(summary = "Compte courant",
            description = "Profil minimal de l'appelant (Bearer requis). Jamais de "
                    + "hash, token ou secret.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`data` = `{id, username, email, displayName, role, emailVerified}`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(Authentication authentication) {
        return ApiResponse.success("Compte courant", authService.me(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Vérifier son email",
            description = "Consomme le token à usage unique (24 h). Rejouer = 400.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Adresse vérifiée"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Lien invalide, expiré ou déjà utilisé",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.success("Adresse email vérifiée", null);
    }

    @Operation(summary = "Renvoyer le lien de vérification",
            description = "Réponse TOUJOURS générique (anti-énumération). Anciens "
                    + "jetons invalidés, nouveau lien émis si compte non vérifié.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Si un compte non vérifié correspond, un lien est envoyé")
    })
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ApiResponse.success(
                "Si un compte non vérifié correspond à cette adresse, un lien vient d'être envoyé", null);
    }

    @Operation(summary = "Mot de passe oublié",
            description = "Réponse TOUJOURS générique, compte existant ou non "
                    + "(anti-énumération). Jeton 1 h à usage unique si compte trouvé.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Si un compte correspond, un email de récupération est envoyé")
    })
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ApiResponse.success(
                "Si un compte correspond à cette adresse, un email de récupération sera envoyé", null);
    }

    @Operation(summary = "Réinitialiser le mot de passe",
            description = "Consomme le token 1 h à usage unique, change le hash et "
                    + "RÉVOQUE toutes les sessions (autres appareils déconnectés).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Mot de passe réinitialisé, reconnectez-vous"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Lien invalide/expiré/utilisé, confirmation ou force",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success("Mot de passe réinitialisé, reconnectez-vous", null);
    }

    @Operation(summary = "Changer de mot de passe (connecté)",
            description = "Mot de passe actuel vérifié, puis TOUTES les sessions sont "
                    + "révoquées (le client se reconnecte avec le nouveau).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Mot de passe changé, reconnectez-vous"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Actuel incorrect, confirmation ou force",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                            Authentication authentication) {
        authService.changePassword(CurrentUser.from(authentication), request);
        return ApiResponse.success("Mot de passe changé, reconnectez-vous", null);
    }

    @Operation(summary = "Lister ses sessions",
            description = "Appareils/sessions non révoquées (page /profile/settings/security). "
                    + "Jamais de token ni de hash.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Liste `{id, deviceInfo, ipAddress, createdAt, lastUsedAt, expiresAt}`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/sessions")
    public ApiResponse<List<SessionDto>> sessions(Authentication authentication) {
        return ApiResponse.success("Sessions actives", authService.sessions(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Révoquer une session",
            description = "404 si inexistante OU appartenant à autrui (anti-énumération "
                    + "et anti accès horizontal).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Session révoquée"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Session introuvable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> revokeSession(@PathVariable Long id, Authentication authentication) {
        authService.revokeSession(CurrentUser.from(authentication), id);
        return ApiResponse.success("Session révoquée", null);
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : value;
    }
}
