package com.streamingafrique.backend.security;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * ROUTE DE TEST du socle sécurité (6.23).
 *
 * Démontre les deux faces de Spring Security sur des routes protégées :
 *   - authentication  : /api/secure/whoami — exige simplement d'être
 *     connecté (Basic). Sans en-tête → 401 ; avec → infos du principal.
*  - authorization   : /api/creator/**, /api/moderator/**, /api/admin/***
 *     — exige un RÔLE précis (hiérarchie USER < CREATOR < MODERATOR < ADMIN).
 *     Un utilisateur authentifié mais sans le bon rôle → 403.
 *
 * Les règles sont portées par @PreAuthorize (sécurité par méthode) ; la
 * configuration HTTP ne garde que la porte d'entrée ("authentifié").
 * Ces endpoints sont des contrôles de fonctionnement, pas de la logique métier.
 */
@Tag(name = "Sécurité (diagnostic)", description = "Sondes d'autorisation : "
        + "vérifient QUI on est et CE qu'on a le droit de voir. Utiles pour tester "
        + "un jeton dans Swagger UI (bouton « Authorize »).")
@RestController
@RequestMapping("/api")
public class SecurityDemoController {

    /** GET /api/secure/whoami — tout utilisateur AUTHENTIFIÉ (n'importe quel rôle). */
    @Operation(summary = "Qui suis-je ?",
            description = "Renvoie le principal du jeton (`principal, authorities, authenticated`). "
                    + "Niveau : tout compte connecté.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Identité de l'appelant"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/secure/whoami")
    public ApiResponse<Map<String, Object>> whoami(Authentication authentication) {
        Map<String, Object> info = new HashMap<>();
        info.put("principal", authentication.getName());
        info.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        info.put("authenticated", authentication.isAuthenticated());
        return ApiResponse.success("Socle sécurité : authentification OK", info);
    }

    /** GET /api/creator/status — rôles CREATOR, MODERATOR ou ADMIN. */
    @Operation(summary = "Sonde créateur",
            description = "Niveau CREATOR ou supérieur (hiérarchie : MODERATOR et ADMIN passent).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Accès accordé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Rôle insuffisant (simple USER)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/creator/status")
    @PreAuthorize("hasAnyRole('CREATOR', 'MODERATOR', 'ADMIN')")
    public ApiResponse<String> creatorStatus(Authentication authentication) {
        return ApiResponse.success("Accès accordé à " + authentication.getName()
                + " (créateur)", "créateur");
    }

    /** GET /api/moderator/status — rôles MODERATOR ou ADMIN. */
    @Operation(summary = "Sonde modérateur",
            description = "Niveau MODERATOR ou supérieur (ADMIN passe, CREATOR non).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Accès accordé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Rôle insuffisant",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/moderator/status")
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN')")
    public ApiResponse<String> moderatorStatus(Authentication authentication) {
        return ApiResponse.success("Accès accordé à " + authentication.getName()
                + " (modérateur)", "modérateur");
    }

    /** GET /api/admin/status — rôle ADMIN uniquement. */
    @Operation(summary = "Sonde administrateur", description = "Niveau ADMIN uniquement.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Accès accordé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/admin/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> adminStatus(Authentication authentication) {
        return ApiResponse.success("Accès accordé à " + authentication.getName()
                + " (administrateur)", "administrateur");
    }
}