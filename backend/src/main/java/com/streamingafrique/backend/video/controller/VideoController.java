package com.streamingafrique.backend.video.controller;

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

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.CreateClipSegmentRequest;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;
import com.streamingafrique.backend.video.dto.LanguageRequest;
import com.streamingafrique.backend.video.dto.PlaybackResponse;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.dto.VideoRequest;
import com.streamingafrique.backend.video.dto.VisibilityRequest;
import com.streamingafrique.backend.video.model.Video;
import com.streamingafrique.backend.video.service.VideoService;
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
 * Controller REST des vidéos VOD — endpoints :
 *   GET    /api/videos/feed              → feed public (PUBLIC)
 *   GET    /api/videos                   → liste paginée et filtrable (PUBLIC, restreinte)
 *   GET    /api/videos/{id}              → une vidéo (PUBLIC : PRIVATE/supprimée = 404)
 *   GET    /api/videos/{id}/playback     → URLs de lecture (PUBLIC : PRIVATE = 404, non READY = 400)
 *   POST   /api/videos/{id}/view         → enregistrer une vue (PUBLIC, anti-abus)
 *   GET    /api/videos/me                → MES vidéos (AUTHENTICATED, brouillons inclus)
 *   GET    /api/videos/admin/all         → supervision (ADMIN)
 *   POST   /api/videos?creatorId=...     → publier              (201, CREATOR)
 *   PATCH  /api/videos/{id}/visibility   → changer la visibilité (CREATOR + garde service)
 *   PUT    /api/videos/{id}              → mettre à jour        (CREATOR)
 *   DELETE /api/videos/{id}              → masquer (logique)    (CREATOR)
 *
 * Autorisation (étape 6.26) : matrice EndpointTier.
 * Lectures : PUBLIC (géré au filtre).
 * Écritures : CREATOR, propriété validée en service — la visibilité utilise
 * la garde requireOwnerOrModerator (le propriétaire CREATOR ou un MODERATOR+
 * peut modérer) ; exiger MODERATOR au contrôleur interdirait au propriétaire
 * de gérer sa propre vidéo.
 *
 * Filtres combinables :
 *   ?page=0&size=20              → pagination (tri via ?sort=title,asc)
 *   ?search=tuto                 → titre OU description contient (insensible casse)
 *   ?category=gaming             → slug de catégorie
 *   ?categoryId=3                → alias « par identifiant de catégorie »
 *   ?creatorId=2                 → chaîne émettrice
 *   ?visibility=PUBLIC           → visibilité (liste privée)
 */
@Tag(name = "Vidéos VOD", description = "Catalogue vidéo : feed et recherche PUBLIC, "
        + "publication et gestion CREATOR (propriétaire ; la visibilité accepte aussi MODERATOR).")
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;
    private final ClipService clipService;

    public VideoController(VideoService videoService, ClipService clipService) {
        this.videoService = videoService;
        this.clipService = clipService;
    }

    /** Feed public — trié des plus récentes aux plus anciennes, filtrable par recherche/catégorie. */
    @Operation(summary = "Feed public des vidéos",
            description = "PUBLIC. Les plus récentes d'abord (12 par page). "
                    + "Filtres : `search` (titre/description), `category` (slug).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `VideoDto` (vidéos `PUBLIC` uniquement)")
    })
    @GetMapping("/feed")
    public ApiResponse<ApiPageResponse<VideoDto>> feed(
            @Parameter(description = "Titre ou description contient", example = "tuto")
            @RequestParam(required = false) String search,
            @Parameter(description = "Slug de catégorie", example = "gaming")
            @RequestParam(required = false) String category,
            @Parameter(description = "Langue déclarée du contenu", example = "fr")
            @RequestParam(required = false) String language,
            @PageableDefault(size = 12, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Feed public",
                videoService.getPublicFeed(search, category, language, pageable));
    }

    @Operation(summary = "Lister les vidéos",
            description = "PUBLIC. Filtres combinables : `search`, `creatorId`, `category` / "
                    + "`categoryId`, `visibility` — la visibilité demandée n'est honorée "
                    + "que pour l'ADMIN ou le propriétaire de la chaîne filtrée, sinon "
                    + "PUBLIC forcé (PRIVATE/supprimées/en traitement exclus).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `VideoDto`")
    })
    @GetMapping
    public ApiResponse<ApiPageResponse<VideoDto>> getAll(
            @RequestParam(required = false) String search,
            @Parameter(description = "Chaîne émettrice") @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Video.Visibility visibility,
            @Parameter(description = "Langue déclarée du contenu", example = "fr")
            @RequestParam(required = false) String language,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        if (categoryId != null && category == null) {
            return ApiResponse.success("Vidéos de la catégorie",
                    videoService.getByCategory(categoryId, pageable));
        }
        return ApiResponse.success("Liste des vidéos",
                videoService.searchAs(search, creatorId, category, visibility,
                        language, CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Lire une vidéo",
            description = "PUBLIC. PRIVATE ou supprimée = 404 (existence masquée) ; "
                    + "le propriétaire/la modération voient le PRIVATE en étant connectés.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`VideoDto` (avec `viewCount`, `processingStatus`)"))
    @GetMapping("/{id}")
    public ApiResponse<VideoDto> getById(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(
                videoService.getVisibleById(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "URLs de lecture",
            description = "PUBLIC (mêmes règles que la lecture). READY exigé "
                    + "(PROCESSING/FAILED → 400) — le player n'essaie jamais un "
                    + "fichier en cours de traitement. Seules des URLs de "
                    + "diffusion sortent (jamais de chemin interne).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`PlaybackResponse` (`playbackUrl`, `durationSeconds`…)"))
    @GetMapping("/{id}/playback")
    public ApiResponse<PlaybackResponse> playback(@PathVariable Long id,
                                                  Authentication authentication) {
        return ApiResponse.success("Lecture",
                videoService.getPlayback(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Enregistrer une vue",
            description = "PUBLIC (connecté ou non). Anti-abus : une vue par "
                    + "viewer et par fenêtre (compte ou IP hachée, §29) ; "
                    + "rafraîchir 1000 fois ne gonfle pas le compteur. Répond "
                    + "le compteur exact.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Compteur de vues dans `data`"))
    @PostMapping("/{id}/view")
    public ApiResponse<Long> recordView(@PathVariable Long id, HttpServletRequest request,
                                        Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Vue enregistrée",
                videoService.recordView(id, ViewerKeys.of(request, actor)));
    }

    @Operation(summary = "Découper un clip depuis cette VOD",
            description = "AUTHENTICATED : tout connecté peut découper une VOD "
                    + "lisible (PUBLIC/UNLISTED + READY). Le serveur vérifie : "
                    + "`startTime >= 0`, `startTime + duration <= durée`, "
                    + "`durée dans [min, max]` ; URL/auteur/chaîne calculés "
                    + "côté serveur (jamais frontend). Naît en PROCESSING "
                    + "(traitement async → READY). Exemple : "
                    + "`{\"title\":\"Moment incroyable\",\"startTimeSeconds\":5150,"
                    + "\"durationSeconds\":25}`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Découpe",
                            value = "{\"title\": \"Moment incroyable\", "
                                    + "\"startTimeSeconds\": 5150, "
                                    + "\"durationSeconds\": 25}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Clip créé en PROCESSING (avec `shareSlug`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Segment invalide, trop long, source non prête",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Vidéo inexistante, privée ou supprimée",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping("/{videoId}/clips")
    public ResponseEntity<ApiResponse<ClipDto>> createClip(
            @PathVariable Long videoId, @Valid @RequestBody CreateClipSegmentRequest request,
            Authentication authentication) {
        ApiResponse<ClipDto> body = ApiResponse.success("Clip en cours de préparation",
                clipService.createFromVideoSegment(videoId, request,
                        CurrentUser.from(authentication)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Mes vidéos",
            description = "AUTHENTICATED : vidéos de MA chaîne (brouillons inclus, "
                    + "supprimées exclues) pour le studio créateur.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `VideoDto`"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/me")
    public ApiResponse<ApiPageResponse<VideoDto>> mine(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Mes vidéos",
                videoService.getMyVideos(CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Supervision des vidéos",
            description = "ADMIN : toutes les vidéos (privées, en traitement, "
                    + "supprimées incluses). Lecture seule.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `VideoDto`"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.ADMIN)
    @GetMapping("/admin/all")
    public ApiResponse<ApiPageResponse<VideoDto>> adminAll(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Vidéos",
                videoService.adminList(CurrentUser.from(authentication), pageable));
    }

    @Operation(summary = "Publier une vidéo",
            description = "CREATOR, propriétaire de la chaîne `creatorId`. "
                    + "Exemple : `POST /api/videos?creatorId=2` "
                    + "`{\"title\":\"Tuto JPA\",\"videoUrl\":\"https://cdn.example.com/a.mp4\","
                    + "\"durationSeconds\":540}` (l'hébergement du fichier reste hors scope).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Publication",
                            value = "{\"title\": \"Tuto JPA\", "
                                    + "\"videoUrl\": \"https://cdn.example.com/a.mp4\", "
                                    + "\"durationSeconds\": 540}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Vidéo publiée"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR` (titre vide, durée ≤ 0, URL invalide…)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou chaîne d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Chaîne inexistante",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping
    public ResponseEntity<ApiResponse<VideoDto>> publish(
            @Parameter(description = "Chaîne émettrice (doit appartenir à l'appelant)")
            @RequestParam Long creatorId, @Valid @RequestBody VideoRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        ApiResponse<VideoDto> body =
                ApiResponse.success("Vidéo publiée", videoService.publish(creatorId, request, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Changer la visibilité",
            description = "Propriétaire de la vidéo OU MODERATOR et plus "
                    + "(`{\"visibility\":\"PRIVATE\"}`, valeurs `PUBLIC|PRIVATE|UNLISTED`). "
                    + "Niveau contrôleur CREATOR : un MODERATOR hérite de CREATOR par hiérarchie.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`VideoDto` avec la nouvelle visibilité"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
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
    public ApiResponse<VideoDto> changeVisibility(
            @PathVariable Long id, @Valid @RequestBody VisibilityRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Visibilité modifiée",
                videoService.changeVisibility(id, request.visibility(), actor));
    }

    @Operation(summary = "Déclarer la langue d'une vidéo",
            description = "Propriétaire OU MODERATOR (`{\"language\":\"fr\"}`, "
                    + "2-3 lettres, `null` = non renseignée). Alimente la "
                    + "découverte par langue — jamais bloquante.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.CREATOR)
    @PatchMapping("/{id}/language")
    public ApiResponse<VideoDto> setLanguage(
            @PathVariable Long id, @Valid @RequestBody LanguageRequest request,
            Authentication authentication) {
        return ApiResponse.success("Langue déclarée",
                videoService.setLanguage(id, request.language(),
                        CurrentUser.from(authentication)));
    }

    @Operation(summary = "Mettre à jour une vidéo", description = "CREATOR, propriétaire.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`VideoDto` mise à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou vidéo d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PutMapping("/{id}")
    public ApiResponse<VideoDto> update(@PathVariable Long id, @Valid @RequestBody VideoRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Vidéo mise à jour", videoService.update(id, request, actor));
    }

    @Operation(summary = "Mettre à jour une vidéo (partiel)",
            description = "CREATOR, propriétaire. Alias PATCH de `PUT /api/videos/{id}` "
                    + "(§23 : le créateur modifie titre, description, miniature, catégorie). "
                    + "Même validation, mêmes permissions.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.CREATOR)
    @PatchMapping("/{id}")
    public ApiResponse<VideoDto> patch(@PathVariable Long id, @Valid @RequestBody VideoRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Vidéo mise à jour", videoService.update(id, request, actor));
    }

    @Operation(summary = "Supprimer une vidéo",
            description = "CREATOR, propriétaire. Suppression LOGIQUE : masquée "
                    + "(détail = 404) mais conservée ; ses clips passent en "
                    + "PRIVATE (désactivés, réversibles) ; fichier conservé.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Vidéo masquée (`data: null`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou vidéo d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        videoService.delete(id, CurrentUser.from(authentication));
        return ApiResponse.success("Vidéo supprimée", null);
    }

    @Operation(summary = "Restaurer une vidéo masquée",
            description = "Propriétaire ou ADMIN. Idempotent (déjà visible → "
                    + "succès sans changement). Ne réactive pas les clips "
                    + "désactivés ni ne republie un brouillon.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`VideoDto` restaurée"))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping("/{id}/restore")
    public ApiResponse<VideoDto> restore(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Vidéo restaurée",
                videoService.restore(id, CurrentUser.from(authentication)));
    }
}