package com.streamingafrique.backend.user.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;
import com.streamingafrique.backend.user.dto.RoleRequest;
import com.streamingafrique.backend.user.dto.UserDto;
import com.streamingafrique.backend.user.dto.UserRequest;
import com.streamingafrique.backend.user.service.UserService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller REST des comptes — endpoints :
 *   GET    /api/users             → liste paginée (PUBLIC, email masqué sauf ADMIN — SEC-002)
 *   GET    /api/users/{id}        → un compte (PUBLIC, email masqué sauf propriétaire/ADMIN — SEC-002)
 *   POST   /api/users             → inscrire (201, PUBLIC)
 *   PUT    /api/users/{id}        → modifier (AUTHENTICATED - propriétaire ou ADMIN)
 *   PUT    /api/users/{id}/role   → changer le rôle (ADMIN)
 *   DELETE /api/users/{id}        → supprimer (ADMIN)
 *
 * Aucune logique métier : validation @Valid + délégation au service.
 *
 * Autorisation (étape 6.26) : matrice EndpointTier appliquée via @PreAuthorize.
 * Lectures et inscription : PUBLIC (géré au filtre SecurityConfig).
 * Profil : AUTHENTICATED (tout connecté), propriété validée en service.
 * Administration comptes/rôles : ADMIN uniquement.
 */
@Tag(name = "Comptes", description = "Inscription (PUBLIC), lecture (PUBLIC), "
        + "profil (connecté, propriétaire ou ADMIN), rôles et suppression (ADMIN).")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Lister les comptes",
            description = "PUBLIC. Liste paginée triée par `createdAt` décroissant. "
                    + "`?page=0&size=10&sort=displayName,asc`. "
                    + "L'email est MASQUÉ (`null`) sauf pour un ADMIN (SEC-002).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `UserDto` (`id, email masqué sauf ADMIN, displayName, avatarUrl, role, createdAt`)")
    })
    @GetMapping
    public ApiResponse<ApiPageResponse<UserDto>> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Liste des comptes",
                userService.getAll(pageable, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Lire un compte", description = "PUBLIC. Email MASQUÉ (`null`) "
            + "sauf pour le propriétaire du compte ou un ADMIN (SEC-002).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`UserDto` (jamais de mot de passe ni de hash ; email masqué sauf propriétaire/ADMIN)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Compte inexistant → `NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ApiResponse<UserDto> getById(
            @Parameter(description = "Identifiant du compte", example = "1") @PathVariable Long id,
            Authentication authentication) {
        return ApiResponse.success(userService.getById(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "S'inscrire",
            description = "PUBLIC. Crée un compte `USER` (rôle attribué ensuite par un ADMIN). "
                    + "Email normalisé en minuscules, mot de passe 8–100 caractères haché en base. "
                    + "Exemple : `{\"email\":\"ada@example.com\",\"displayName\":\"Ada\",\"password\":\"motdepasse-xyz\"}`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Inscription",
                            value = "{\"email\": \"ada@example.com\", \"displayName\": \"Ada\", "
                                    + "\"password\": \"motdepasse-xyz\"}"))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Compte créé : `UserDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Champs invalides → `VALIDATION_ERROR` (détail par champ dans `errors`)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Email déjà utilisé (insensible à la casse) → `CONFLICT`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429",
                    description = "Trop d'inscriptions (voir `Retry-After`)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> create(@Valid @RequestBody UserRequest request) {
        ApiResponse<UserDto> body = ApiResponse.success("Compte créé", userService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Modifier son profil",
            description = "Connecté (Bearer ou Basic). Seul le PROPRIÉTAIRE du compte ou un ADMIN. "
                    + "Modifie nom d'affichage et avatar (l'email reste celui du compte).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`UserDto` mis à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Ni propriétaire ni ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Nouvel email déjà utilisé → `CONFLICT`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PutMapping("/{id}")
    public ApiResponse<UserDto> update(@PathVariable Long id, @Valid @RequestBody UserRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Compte mis à jour", userService.update(id, request, actor));
    }

    @Operation(summary = "Changer le rôle d'un compte",
            description = "ADMIN uniquement (`{\"role\":\"CREATOR\"}`, valeurs `USER|CREATOR|MODERATOR|ADMIN`). "
                    + "Un ADMIN ne peut pas modifier son PROPRE rôle (ni se promouvoir, ni se dégrader).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`UserDto` avec le nouveau rôle"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non ADMIN, ou ADMIN sur son propre compte",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.ADMIN)
    @PutMapping("/{id}/role")
    public ApiResponse<UserDto> changeRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Rôle modifié", userService.changeRole(id, request.role(), actor));
    }

    @Operation(summary = "Supprimer un compte",
            description = "ADMIN uniquement. Un ADMIN ne peut pas supprimer son PROPRE compte.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Compte supprimé (`data: null`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non ADMIN, ou ADMIN sur son propre compte",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.ADMIN)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        userService.delete(id, CurrentUser.from(authentication));
        return ApiResponse.success("Compte supprimé", null);
    }
}