package com.streamingafrique.backend.clip.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.ClipRequest;
import com.streamingafrique.backend.clip.dto.ClipVisibilityRequest;
import com.streamingafrique.backend.clip.model.Clip;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;
import com.streamingafrique.backend.video.dto.PlaybackResponse;
import com.streamingafrique.backend.video.service.ViewerKeys;

import jakarta.servlet.http.HttpServletRequest;
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
 * Controller REST des clips — endpoints :
 *   GET    /api/clips/feed              → feed public (PUBLIC)
 *   GET    /api/clips                   → liste paginée (PUBLIC, restreinte)
 *   GET    /api/clips/{id}              → un clip (PUBLIC : PRIVATE/supprimé = 404)
 *   GET    /api/clips/{id}/playback     → URLs de lecture (PUBLIC, READY exigé)
 *   POST   /api/clips/{id}/view         → enregistrer une vue (PUBLIC, anti-abus)
 *   GET    /api/clips/me                → MES découpages (AUTHENTICATED)
 *   GET    /api/clips/admin/all         → supervision (ADMIN)
 *   POST   /api/clips/from-stream       → clip extrait d'un direct   (201, CREATOR)
 *   POST   /api/clips/from-video        → clip extrait d'une vidéo   (201, CREATOR)
 *   PUT    /api/clips/{id}              → mettre à jour             (CREATOR)
 *   PATCH  /api/clips/{id}/visibility   → changer la visibilité (CREATOR + garde service)
 *   DELETE /api/clips/{id}              → masquer (logique)         (CREATOR)
 *   POST   /api/clips/{id}/restore      → restaurer                 (CREATOR)
 *
 * Le découpage VIEWER (tout connecté) vit sur les sources :
 *   POST /api/videos/{videoId}/clips et POST /api/streams/{streamId}/clips.
 *
 * Autorisation (étape 6.26) : matrice EndpointTier.
 * Lectures : PUBLIC (géré au filtre).
 * Écritures : CREATOR, propriété validée en service (MODERATOR+ pour suppression d'autrui).
 */
@Tag(name = "Clips", description = "Extraits courts : feed et lecture PUBLIC, "
        + "création/mise à jour CREATOR (source obligatoirement de sa propre chaîne).")
@RestController
@RequestMapping("/api/clips")
public class ClipController {

    private final ClipService clipService;

    public ClipController(ClipService clipService) {
        this.clipService = clipService;
    }

    @Operation(summary = "Feed public des clips",
            description = "PUBLIC. Les plus récents d'abord (12 par page), filtrable par `search`.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `ClipDto`")
    })
    @GetMapping("/feed")
    public ApiResponse<ApiPageResponse<ClipDto>> feed(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 12, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Feed de clips", clipService.getFeed(search, pageable));
    }

    @Operation(summary = "Lister les clips",
            description = "PUBLIC. Filtres combinables : `search`, `creatorId`, `streamId`, "
                    + "`videoId` — visibilité PUBLIC forcée sauf ADMIN/propriétaire "
                    + "de la chaîne filtrée (PRIVATE/supprimés/en traitement exclus).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `ClipDto` (`streamId` OU `videoId` selon la source)")
    })
    @GetMapping
    public ApiResponse<ApiPageResponse<ClipDto>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long streamId,
            @RequestParam(required = false) Long videoId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Liste des clips",
                clipService.searchAs(search, creatorId, streamId, videoId, null,
                        CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Lire un clip",
            description = "PUBLIC. PRIVATE ou supprimé = 404 (existence masquée).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`ClipDto` (avec `shareSlug`, `viewCount`)"))
    @GetMapping("/{id}")
    public ApiResponse<ClipDto> getById(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(
                clipService.getVisibleById(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Lire un clip par lien court",
            description = "PUBLIC. Résout le lien de partage `/c/{shareSlug}` "
                    + "(§40-41 : copier le lien, fonctionne sans connexion si PUBLIC). "
                    + "PRIVATE ou supprimé = 404 (existence masquée) ; le propriétaire "
                    + "et la modération voient le PRIVATE en étant connectés.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`ClipDto` (avec `shareSlug`, `viewCount`)"))
    @GetMapping("/c/{shareSlug}")
    public ApiResponse<ClipDto> getByShareSlug(@PathVariable String shareSlug,
                                              Authentication authentication) {
        return ApiResponse.success(
                clipService.getVisibleByShareSlug(shareSlug, CurrentUser.from(authentication)));
    }

    @Operation(summary = "URLs de lecture",
            description = "PUBLIC (mêmes règles que la lecture). READY exigé. "
                    + "`startsAtSeconds` = position dans la source (le player "
                    + "démarre à l'extrait).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`PlaybackResponse`"))
    @GetMapping("/{id}/playback")
    public ApiResponse<PlaybackResponse> playback(@PathVariable Long id,
                                                  Authentication authentication) {
        return ApiResponse.success("Lecture",
                clipService.getPlayback(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Enregistrer une vue",
            description = "PUBLIC (connecté ou non). Anti-abus : une vue par "
                    + "viewer et par fenêtre. Répond le compteur exact.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Compteur de vues dans `data`"))
    @PostMapping("/{id}/view")
    public ApiResponse<Long> recordView(@PathVariable Long id, HttpServletRequest request,
                                        Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Vue enregistrée",
                clipService.recordView(id, ViewerKeys.of(request, actor)));
    }

    @Operation(summary = "Mes clips découpés",
            description = "AUTHENTICATED : extraits découpés par MOI (toutes "
                    + "chaînes sources, suppressions exclues).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `ClipDto`"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/me")
    public ApiResponse<ApiPageResponse<ClipDto>> mine(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Mes clips",
                clipService.getMyClips(CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Supervision des clips",
            description = "ADMIN : tous les clips (privés, en traitement, "
                    + "supprimés inclus). Lecture seule.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `ClipDto`"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.ADMIN)
    @GetMapping("/admin/all")
    public ApiResponse<ApiPageResponse<ClipDto>> adminAll(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Clips",
                clipService.adminList(CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Créer un clip depuis un direct",
            description = "CREATOR. Le direct source doit appartenir à la chaîne `creatorId` "
                    + "de l'appelant (vol de contenu d'autrui → `403`). "
                    + "Exemple : `POST /api/clips/from-stream?creatorId=2&streamId=5` "
                    + "`{\"title\":\"Meilleur moment\",\"clipUrl\":\"https://cdn.example.com/c.mp4\","
                    + "\"durationSeconds\":25}`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Clip",
                            value = "{\"title\": \"Meilleur moment\", "
                                    + "\"clipUrl\": \"https://cdn.example.com/c.mp4\", "
                                    + "\"durationSeconds\": 25}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Clip créé (avec `streamId`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Source appartenant à une autre chaîne",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Chaîne ou direct inexistant",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping("/from-stream")
    public ResponseEntity<ApiResponse<ClipDto>> createFromStream(
            @Parameter(description = "Chaîne propriétaire (doit appartenir à l'appelant)")
            @RequestParam Long creatorId,
            @Parameter(description = "Direct source") @RequestParam Long streamId,
            @Valid @RequestBody ClipRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        ApiResponse<ClipDto> body =
                ApiResponse.success("Clip créé", clipService.createFromStream(creatorId, streamId, request, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Créer un clip depuis une vidéo",
            description = "CREATOR. Même règle de propriété que depuis un direct : "
                    + "la vidéo source doit appartenir à la chaîne `creatorId` de l'appelant.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Clip créé (avec `videoId`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Source appartenant à une autre chaîne",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Chaîne ou vidéo inexistante",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping("/from-video")
    public ResponseEntity<ApiResponse<ClipDto>> createFromVideo(
            @Parameter(description = "Chaîne propriétaire (doit appartenir à l'appelant)")
            @RequestParam Long creatorId,
            @Parameter(description = "Vidéo source") @RequestParam Long videoId,
            @Valid @RequestBody ClipRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        ApiResponse<ClipDto> body =
                ApiResponse.success("Clip créé", clipService.createFromVideo(creatorId, videoId, request, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Mettre à jour un clip", description = "CREATOR, propriétaire.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`ClipDto` mis à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Clip d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PutMapping("/{id}")
    public ApiResponse<ClipDto> update(@PathVariable Long id, @Valid @RequestBody ClipRequest request,
            Authentication authentication) {
        return ApiResponse.success("Clip mis à jour",
                clipService.update(id, request, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Mettre à jour un clip (partiel)",
            description = "CREATOR, propriétaire. Alias PATCH de `PUT /api/clips/{id}` "
                    + "(§68 : titre, miniature, visibilité — le backend revalide).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.CREATOR)
    @PatchMapping("/{id}")
    public ApiResponse<ClipDto> patch(@PathVariable Long id, @Valid @RequestBody ClipRequest request,
            Authentication authentication) {
        return ApiResponse.success("Clip mis à jour",
                clipService.update(id, request, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Changer la visibilité",
            description = "Propriétaire du clip OU MODERATOR et plus "
                    + "(`{\"visibility\":\"PRIVATE\"}`, valeurs `PUBLIC|PRIVATE|UNLISTED`). "
                    + "Niveau contrôleur CREATOR : un MODERATOR hérite de CREATOR par hiérarchie.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`ClipDto` avec la nouvelle visibilité"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Ni propriétaire ni MODERATOR",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PatchMapping("/{id}/visibility")
    public ApiResponse<ClipDto> changeVisibility(
            @PathVariable Long id, @Valid @RequestBody ClipVisibilityRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Visibilité modifiée",
                clipService.changeVisibility(id, request.visibility(), actor));
    }

    @Operation(summary = "Supprimer un clip",
            description = "CREATOR, propriétaire (MODERATOR et plus pour le clip d'autrui). "
                    + "Suppression LOGIQUE : masqué (détail = 404) mais conservé.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Clip masqué (`data: null`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Clip d'autrui sans rôle de modération",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        clipService.delete(id, CurrentUser.from(authentication));
        return ApiResponse.success("Clip supprimé", null);
    }

    @Operation(summary = "Restaurer un clip masqué",
            description = "Propriétaire ou modération. Idempotent.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`ClipDto` restauré"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping("/{id}/restore")
    public ApiResponse<ClipDto> restore(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Clip restauré",
                clipService.restore(id, CurrentUser.from(authentication)));
    }
}