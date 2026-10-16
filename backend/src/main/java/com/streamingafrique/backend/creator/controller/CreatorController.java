package com.streamingafrique.backend.creator.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.creator.dto.CreatorProfileDto;
import com.streamingafrique.backend.creator.dto.CreatorProfileRequest;
import com.streamingafrique.backend.creator.service.CreatorService;
import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;
import com.streamingafrique.backend.video.dto.VideoDto;
import com.streamingafrique.backend.video.service.VideoService;

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
 * Controller REST des fiches créateurs (chaînes) — endpoints :
 *   GET    /api/creators                 → liste paginée et filtrable (PUBLIC)
 *   GET    /api/creators/{id}            → une fiche (PUBLIC)
 *   GET    /api/creators/slug/{slug}     → fiche par URL publique (PUBLIC)
 *   GET    /api/creators/user/{userId}   → fiche d'un compte (PUBLIC)
 *   POST   /api/creators?userId=...      → créer sa chaîne      (201, AUTHENTICATED)
 *   PUT    /api/creators/{id}            → mettre à jour        (CREATOR)
 *   DELETE /api/creators/{id}            → supprimer            (CREATOR)
 *
 * Autorisation (étape 6.26) : matrice EndpointTier.
 * Lectures : PUBLIC (géré au filtre).
 * Création : AUTHENTICATED — c'est l'acte qui PROMEUT un USER en CREATOR
 * (le service vérifie requireSelf puis change le rôle) ; exiger CREATOR ici
 * rendrait la promotion impossible (œuf et poule).
 * Mise à jour / suppression : CREATOR, propriété validée en service.
 * Le ?userId= de création est conservé pour compatibilité mais doit être celui de
 * l'acteur authentifié (requireSelf) — il ne fait plus autorité.
 * Recherche/tri/pagination gérés en base.
 */
@Tag(name = "Chaînes", description = "Fiches créateurs : lecture PUBLIC, ouverture "
        + "de chaîne (tout connecté — PROMEUT en CREATOR), gestion CREATOR (propriétaire).")
@RestController
@RequestMapping("/api/creators")
public class CreatorController {

    private final CreatorService creatorService;
    private final VideoService videoService;
    private final ClipService clipService;

    public CreatorController(CreatorService creatorService, VideoService videoService,
                             ClipService clipService) {
        this.creatorService = creatorService;
        this.videoService = videoService;
        this.clipService = clipService;
    }

    @Operation(summary = "Lister les chaînes",
            description = "PUBLIC. Recherche `search` (nom/slug) + pagination.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `CreatorProfileDto`")
    })
    @GetMapping
    public ApiResponse<ApiPageResponse<CreatorProfileDto>> getAll(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Liste des chaînes", creatorService.search(search, pageable));
    }

    @Operation(summary = "Lire une chaîne par slug",
            description = "PUBLIC. Le slug est l'identifiant public d'URL (`/factu-congo`).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CreatorProfileDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/slug/{slug}")
    public ApiResponse<CreatorProfileDto> getBySlug(
            @Parameter(description = "Slug public de la chaîne", example = "etapes-astuces")
            @PathVariable String slug) {
        return ApiResponse.success(creatorService.getBySlug(slug));
    }

    @Operation(summary = "Lire ma chaîne",
            description = "AUTHENTICATED. Propriétaire dérivé du JWT (jamais d'un "
                    + "`userId` frontend). 404 si le compte n'a pas encore de chaîne. "
                    + "Équivalent `GET /api/channels/me` de la spec (convention "
                    + "existante : `/api/creators`).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CreatorProfileDto` du connecté"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Ce compte n'a pas encore de chaîne → `NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/me")
    public ApiResponse<CreatorProfileDto> getMine(Authentication authentication) {
        return ApiResponse.success("Ma chaîne",
                creatorService.getMyChannel(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Lire la chaîne d'un compte", description = "PUBLIC (`userId` = compte).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CreatorProfileDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Ce compte n'a pas de chaîne → `NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/user/{userId}")
    public ApiResponse<CreatorProfileDto> getByUserId(@PathVariable Long userId) {
        return ApiResponse.success(creatorService.getByUserId(userId));
    }

    @Operation(summary = "Lire une chaîne par id", description = "PUBLIC.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CreatorProfileDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ApiResponse<CreatorProfileDto> getById(@PathVariable Long id) {
        return ApiResponse.success(creatorService.getById(id));
    }

    @Operation(summary = "Vidéos d'une chaîne",
            description = "PUBLIC. Équivalent `GET /api/channels/{channelId}/videos` de la spec "
                    + "(§22/§43/§59 : la chaîne = CreatorProfile). VOD `PUBLIC`, paginées, "
                    + "les plus récentes d'abord.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `VideoDto`")
    })
    @GetMapping("/{id}/videos")
    public ApiResponse<ApiPageResponse<VideoDto>> getVideos(
            @Parameter(description = "Chaîne émettrice") @PathVariable Long id,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Vidéos de la chaîne", videoService.getByCreator(id, pageable));
    }

    @Operation(summary = "Clips d'une chaîne",
            description = "PUBLIC. Clips `PUBLIC` de la chaîne (source : ses lives/VOD), "
                    + "paginés, les plus récents d'abord (§44/§59).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `ClipDto`")
    })
    @GetMapping("/{id}/clips")
    public ApiResponse<ApiPageResponse<ClipDto>> getClips(
            @Parameter(description = "Chaîne source") @PathVariable Long id,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success("Clips de la chaîne", clipService.getByCreator(id, pageable));
    }

    @Operation(summary = "Ouvrir sa chaîne",
            description = "Tout compte connecté. C'est l'acte qui PROMEUT un USER en CREATOR. "
                    + "`userId` doit être celui de l'appelant (conservé pour compatibilité, "
                    + "ne fait plus autorité). Une seule chaîne par compte. "
                    + "Exemple : `POST /api/creators?userId=4` `{\"displayName\":\"Ma Chaîne\"}`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Ouverture",
                            value = "{\"displayName\": \"Ma Chaîne\"}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Chaîne créée (le compte devient CREATOR)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "`userId` d'un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Le compte a déjà une chaîne → `CONFLICT`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping
    public ResponseEntity<ApiResponse<CreatorProfileDto>> create(
            @Parameter(description = "Compte de l'appelant (doit être soi-même)")
            @RequestParam Long userId, @Valid @RequestBody CreatorProfileRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        ApiResponse<CreatorProfileDto> body =
                ApiResponse.success("Chaîne créée", creatorService.create(userId, request, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Mettre à jour sa chaîne",
            description = "CREATOR, propriétaire (nom, bio — le slug suit le nom).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CreatorProfileDto` mise à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Chaîne d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PutMapping("/{id}")
    public ApiResponse<CreatorProfileDto> update(
            @PathVariable Long id, @Valid @RequestBody CreatorProfileRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        return ApiResponse.success("Chaîne mise à jour", creatorService.update(id, request, actor));
    }

    @Operation(summary = "Fermer sa chaîne",
            description = "CREATOR, propriétaire. FERMETURE logique (statut CLOSED, "
                    + "invisible en public) — JAMAIS de suppression physique : streams, "
                    + "vidéos, follows, abonnements et modération restent intacts.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Chaîne fermée (`data: null`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Chaîne d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        creatorService.delete(id, CurrentUser.from(authentication));
        return ApiResponse.success("Chaîne fermée", null);
    }

    @Operation(summary = "Changer l'avatar de sa chaîne",
            description = "CREATOR, propriétaire. Upload sécurisé + liaison + "
                    + "invalidation du cache + suppression de l'ancien fichier stocké.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CreatorProfileDto` mise à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Chaîne d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
                    description = "Image trop volumineuse",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415",
                    description = "Type non autorisé (contenu réel)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CreatorProfileDto> updateAvatar(
            @PathVariable Long id, @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ApiResponse.success("Avatar de chaîne mis à jour",
                creatorService.updateAvatar(id, file, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Changer la bannière de sa chaîne",
            description = "CREATOR, propriétaire. Mêmes garanties que l'avatar. "
                    + "Dimensions RECOMMANDÉES 16:6 côté interface ; le backend ne "
                    + "bloque pas sur les dimensions (formats d'écran variés).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CreatorProfileDto` mise à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Chaîne d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
                    description = "Image trop volumineuse",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415",
                    description = "Type non autorisé (contenu réel)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping(value = "/{id}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CreatorProfileDto> updateBanner(
            @PathVariable Long id, @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ApiResponse.success("Bannière de chaîne mise à jour",
                creatorService.updateBanner(id, file, CurrentUser.from(authentication)));
    }
}