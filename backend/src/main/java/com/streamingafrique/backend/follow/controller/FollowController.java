package com.streamingafrique.backend.follow.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.follow.dto.FollowDto;
import com.streamingafrique.backend.follow.dto.FollowRequest;
import com.streamingafrique.backend.follow.service.FollowService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

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
 * Controller REST des suivis — endpoints :
 *   POST   /api/follows                        → suivre une chaîne      (201, AUTHENTICATED)
 *   DELETE /api/follows?followerId=&channelId= → ne plus suivre         (AUTHENTICATED)
 *   GET    /api/follows/is-following?followerId=&channelId= → Booléen (PUBLIC)
 *   GET    /api/follows/{followerId}/following → chaînes suivies (PUBLIC)
 *   GET    /api/follows/followers-count?channelId=   → nb d'abonnés (PUBLIC)
 *   GET    /api/follows/following-count?followerId=  → nb de suivis (PUBLIC)
 *
 * Autorisation (étape 6.26) : matrice EndpointTier.
 * Écritures (suivre/ne plus suivre) : AUTHENTICATED (tout connecté), propriété validée en service.
 * Lectures : PUBLIC.
 */
@Tag(name = "Suivis", description = "Abonnements aux chaînes (gratuit) : écritures "
        + "connecté avec `followerId` = soi-même (même ADMIN), lectures PUBLIC. "
        + "Suivre déclenche une notification `NEW_FOLLOWER` au propriétaire.")
@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @Operation(summary = "Suivre une chaîne",
            description = "Tout compte connecté, pour SOI-MÊME (`followerId` = id de l'appelant — "
                    + "agir au nom d'autrui → `403`, même ADMIN). Idempotent : suivre deux fois "
                    + "renvoie le suivi existant. On ne peut pas suivre sa propre chaîne (`400`). "
                    + "Exemple : `{\"followerId\": 4, \"channelId\": 2}`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Suivi",
                            value = "{\"followerId\": 4, \"channelId\": 2}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "`FollowDto` (`id, followerId, followedId, createdAt`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Auto-suivi ou corps invalide",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "`followerId` d'un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Compte ou chaîne inexistant",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping
    public ResponseEntity<ApiResponse<FollowDto>> follow(@Valid @RequestBody FollowRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        ApiResponse<FollowDto> body = ApiResponse.success(
                "Chaîne suivie", followService.follow(request.followerId(), request.channelId(), actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Ne plus suivre",
            description = "Tout compte connecté, pour SOI-MÊME. Ex. "
                    + "`DELETE /api/follows?followerId=4&channelId=2`. "
                    + "Ne pas suivre la chaîne → `404 NOT_FOUND`.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Désabonnement effectué (`data: null`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "`followerId` d'un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Suivi inexistant → `NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @DeleteMapping
    public ApiResponse<Void> unfollow(
            @Parameter(description = "Abonné (doit être soi-même)") @RequestParam Long followerId,
            @Parameter(description = "Chaîne") @RequestParam Long channelId,
            Authentication authentication) {
        followService.unfollow(followerId, channelId, CurrentUser.from(authentication));
        return ApiResponse.success("Chaîne ne suivie", null);
    }

    @Operation(summary = "Est-ce suivi ?", description = "PUBLIC. Ex. "
            + "`/api/follows/is-following?followerId=4&channelId=2` → `data: true|false`.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Booléen dans `data`"))
    @GetMapping("/is-following")
    public ApiResponse<Boolean> isFollowing(@RequestParam Long followerId, @RequestParam Long channelId) {
        return ApiResponse.success(followService.isFollowing(followerId, channelId));
    }

    @Operation(summary = "Chaînes suivies par un utilisateur",
            description = "PUBLIC, paginé (fiches publiques).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `CreatorProfileDto`"))
    @GetMapping("/{followerId}/following")
    public ApiResponse<ApiPageResponse<CreatorProfileDto>> followedChannels(
            @PathVariable Long followerId, @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success("Chaînes suivies", followService.getFollowedChannels(followerId, pageable));
    }

    @Operation(summary = "Nombre d'abonnés d'une chaîne",
            description = "PUBLIC.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Compteur dans `data`"))
    @GetMapping("/followers-count")
    public ApiResponse<Long> followersCount(@RequestParam Long channelId) {
        return ApiResponse.success(followService.countFollowers(channelId));
    }

    @Operation(summary = "Nombre de chaînes suivies",
            description = "PUBLIC.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Compteur dans `data`"))
    @GetMapping("/following-count")
    public ApiResponse<Long> followingCount(@RequestParam Long followerId) {
        return ApiResponse.success(followService.countFollowing(followerId));
    }
}