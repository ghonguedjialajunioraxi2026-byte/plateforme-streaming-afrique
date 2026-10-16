package com.streamingafrique.backend.stream.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.chat.service.ChatHistoryService;
import com.streamingafrique.backend.clip.dto.ClipDto;
import com.streamingafrique.backend.clip.dto.CreateClipSegmentRequest;
import com.streamingafrique.backend.clip.service.ClipService;
import com.streamingafrique.backend.moderation.dto.ChatMessageDto;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;
import com.streamingafrique.backend.stream.dto.StreamDto;
import com.streamingafrique.backend.stream.dto.StreamKeyResponse;
import com.streamingafrique.backend.stream.dto.IngestInfoDto;
import com.streamingafrique.backend.stream.dto.StreamRequest;
import com.streamingafrique.backend.stream.model.Stream;
import com.streamingafrique.backend.stream.service.StreamService;

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
 * Controller REST des directs — endpoints :
 *   GET    /api/streams                  → liste paginée et filtrable
 *                                         (?search= ?creatorId= ?category= ?status=)
 *   GET    /api/streams/{id}             → un direct
 *   POST   /api/streams?creatorId=...    → programmer          (201, CREATOR)
 *   POST   /api/streams/{id}/start       → lancer le direct    (CREATOR)
 *   POST   /api/streams/{id}/end         → terminer le direct  (CREATOR, VOD auto)
 *   POST   /api/streams/{streamId}/clips → découper un clip    (201, AUTHENTICATED)
 *   PUT    /api/streams/{id}             → réprogrammer        (CREATOR)
 *   DELETE /api/streams/{id}             → supprimer           (CREATOR)
 *
 * Autorisation (étape 6.26) : matrice EndpointTier.
 * Lectures : PUBLIC (géré au filtre).
 * Écritures : CREATOR (création/publication de contenu), propriété validée en service.
 *
 * Le vrai streaming (flux vidéo/audio) reste hors scope.
 *
 * Filtres combinables librement :
 *   ?page=0&size=20         → pagination (tri : ?sort=title,asc&sort=createdAt,desc)
 *   ?search=foot            → titre contient "foot" (insensible à la casse)
 *   ?category=gaming        → slug de catégorie
 *   ?categoryId=3           → alias « par identifiant de catégorie »
 *   ?status=LIVE            → statut du direct
 *   ?creatorId=2            → chaîne émettrice
 */
@Tag(name = "Directs", description = "Programmation et cycle de vie des directs "
        + "(`SCHEDULED → LIVE → ENDED`). Lectures PUBLIC, écritures CREATOR (propriétaire de la chaîne).")
@RestController
@RequestMapping("/api/streams")
public class StreamController {

    private final StreamService streamService;
    private final ClipService clipService;
    private final ChatHistoryService chatHistory;

    public StreamController(StreamService streamService, ClipService clipService,
                            ChatHistoryService chatHistory) {
        this.streamService = streamService;
        this.clipService = clipService;
        this.chatHistory = chatHistory;
    }

    @Operation(summary = "Lister les directs",
            description = "PUBLIC. Filtres combinables : `search` (titre, insensible à la casse), "
                    + "`creatorId`, `category` (slug) ou `categoryId`, `status` (`SCHEDULED|LIVE|ENDED`). "
                    + "Pagination `?page=0&size=10&sort=scheduledStartAt,asc`. La liste « en direct » "
                    + "(`?status=LIVE` seul) est servie depuis le cache.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `StreamDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Statut ou tri invalide → `BAD_PARAM` / `SORT_INVALID`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping
    public ApiResponse<ApiPageResponse<StreamDto>> getAll(
            @Parameter(description = "Titre contient (insensible à la casse)", example = "tournoi")
            @RequestParam(required = false) String search,
            @Parameter(description = "Chaîne émettrice", example = "2")
            @RequestParam(required = false) Long creatorId,
            @Parameter(description = "Catégorie par identifiant (alias historique)")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Catégorie par slug", example = "gaming")
            @RequestParam(required = false) String category,
            @Parameter(description = "Statut du direct", example = "LIVE")
            @RequestParam(required = false) Stream.Status status,
            @Parameter(description = "Langue déclarée du direct", example = "fr")
            @RequestParam(required = false) String language,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        String categorySlug = category != null ? category : null;
        if (categoryId != null && categorySlug == null) {
            // Alias historique : ?categoryId=N filtre par identifiant de catégorie
            // (requête en base, jamais de chargement complet en mémoire).
            return ApiResponse.success("Directs de la catégorie",
                    streamService.getByCategory(categoryId, pageable));
        }
        // Page « en direct » : seule le statut filtre → liste très consultée, servie
        // depuis le cache Redis (invalidee sur chaque écriture, voir StreamService).
        if (search == null && creatorId == null && categorySlug == null && status != null
                && language == null) {
            return ApiResponse.success("Directs par statut",
                    streamService.getByStatus(status, pageable));
        }
        return ApiResponse.success("Liste des directs",
                streamService.search(search, creatorId, categorySlug, status, language, pageable));
    }

    @Operation(summary = "Lire un direct", description = "PUBLIC.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`StreamDto` (`id, title, description, status, scheduledStartAt, "
                            + "startedAt, endedAt, creatorId, category…`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ApiResponse<StreamDto> getById(@PathVariable Long id) {
        return ApiResponse.success(streamService.getById(id));
    }

    @Operation(summary = "Historique chat public d'un direct",
            description = "PUBLIC (visiteur inclus) : derniers messages VISIBLES du salon, "
                    + "anciens d'abord. Les messages masqués par la modération n'y "
                    + "apparaissent jamais (modération uniquement).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `ChatMessageDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}/chat")
    public ApiResponse<ApiPageResponse<ChatMessageDto>> chatHistory(
            @PathVariable Long id,
            @Parameter(description = "Nombre de messages (1..50)", example = "30")
            @RequestParam(required = false, defaultValue = "30") int size) {
        return ApiResponse.success("Messages", chatHistory.recent(id, size));
    }

    @Operation(summary = "Programmer un direct",
            description = "CREATOR, propriétaire de la chaîne `creatorId` (passé en paramètre de "
                    + "requête, doit appartenir à l'acteur authentifié). Statut initial `SCHEDULED`. "
                    + "Exemple : `POST /api/streams?creatorId=2` "
                    + "`{\"title\":\"Tournoi FIFA\",\"scheduledStartAt\":\"2026-10-01T20:00:00Z\"}`.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Programmation",
                            value = "{\"title\": \"Tournoi FIFA\", "
                                    + "\"scheduledStartAt\": \"2026-10-01T20:00:00Z\"}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Direct programmé (`status: SCHEDULED`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR, ou chaîne d'un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Chaîne `creatorId` inexistante → `NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping
    public ResponseEntity<ApiResponse<StreamDto>> schedule(
            @Parameter(description = "Chaîne émettrice (doit appartenir à l'appelant)", example = "2")
            @RequestParam Long creatorId, @Valid @RequestBody StreamRequest request,
            Authentication authentication) {
        CurrentUser actor = CurrentUser.from(authentication);
        ApiResponse<StreamDto> body =
                ApiResponse.success("Direct programmé", streamService.schedule(creatorId, request, actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Lancer le direct",
            description = "CREATOR, propriétaire. `SCHEDULED → LIVE`. Relancer un direct "
                    + "déjà en cours → `400`.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`StreamDto` (`status: LIVE`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Transition impossible (ex. déjà `LIVE`)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou direct d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping("/{id}/start")
    public ApiResponse<StreamDto> start(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Direct lancé", streamService.start(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Infos d'ingest (page OBS)",
            description = "CREATOR, propriétaire : URL RTMP, URL HLS, protocole, présence "
                    + "d'une clé. JAMAIS la clé en clair (génération/rotation uniquement).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`IngestInfoDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou direct d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @GetMapping("/{id}/ingest")
    public ApiResponse<IngestInfoDto> ingest(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Ingest",
                streamService.ingestInfo(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Émettre / régénérer la stream key",
            description = "CREATOR, propriétaire : génère une clé (`sa_…`, 256 bits), "
                    + "stocke SEULEMENT l'empreinte SHA-256 et renvoie la clé EN CLAIR "
                    + "UNE SEULE FOIS (la copier dans OBS : `<id>?key=<clé>`). "
                    + "Régénérer invalide l'ancienne clé.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`StreamKeyResponse` (clé en clair unique)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou direct d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping("/{id}/stream-key")
    public ApiResponse<StreamKeyResponse> issueKey(@PathVariable Long id,
                                                   Authentication authentication) {
        return ApiResponse.success("Clé émise — copiez-la maintenant, elle ne sera plus réaffichée",
                streamService.issueStreamKey(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Terminer le direct",
            description = "CREATOR, propriétaire. `LIVE → ENDED`. Terminer un direct non lancé → `400`.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`StreamDto` (`status: ENDED`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Transition impossible",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou direct d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PostMapping("/{id}/end")
    public ApiResponse<StreamDto> end(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Direct terminé — VOD en cours de préparation",
                streamService.end(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Découper un clip depuis ce direct",
            description = "AUTHENTICATED : tout connecté, direct LIVE ou terminé, "
                    + "lisible (PUBLIC/UNLISTED). Mêmes validations serveur que "
                    + "depuis une VOD (bornes écoulées, durée [min, max]). "
                    + "Sans enregistrement disponible → 400 (préférer la VOD).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Découpe",
                            value = "{\"title\": \"But incroyable\", "
                                    + "\"startTimeSeconds\": 300, "
                                    + "\"durationSeconds\": 20}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Clip créé en PROCESSING (avec `shareSlug`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Segment invalide, direct non découpable",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Direct inexistant ou non lisible",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping("/{streamId}/clips")
    public ResponseEntity<ApiResponse<ClipDto>> createClip(
            @PathVariable Long streamId, @Valid @RequestBody CreateClipSegmentRequest request,
            Authentication authentication) {
        ApiResponse<ClipDto> body = ApiResponse.success("Clip en cours de préparation",
                clipService.createFromStreamSegment(streamId, request,
                        CurrentUser.from(authentication)));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Reprogrammer un direct",
            description = "CREATOR, propriétaire (titre, description, date).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`StreamDto` mis à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou direct d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @PutMapping("/{id}")
    public ApiResponse<StreamDto> update(@PathVariable Long id, @Valid @RequestBody StreamRequest request,
            Authentication authentication) {
        return ApiResponse.success("Direct mis à jour",
                streamService.update(id, request, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Supprimer un direct",
            description = "CREATOR, propriétaire. Suppression LOGIQUE : masqué "
                    + "des listes (détail = 404) mais conservé — VOD et clips "
                    + "liés survivent.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Direct supprimé (`data: null`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non CREATOR ou direct d'autrui",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.CREATOR)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        streamService.delete(id, CurrentUser.from(authentication));
        return ApiResponse.success("Direct supprimé", null);
    }
}