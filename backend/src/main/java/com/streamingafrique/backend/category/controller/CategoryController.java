package com.streamingafrique.backend.category.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.category.dto.CategoryDto;
import com.streamingafrique.backend.category.dto.CategoryRequest;
import com.streamingafrique.backend.category.service.CategoryService;
import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller REST des catégories — endpooints :
 *   GET    /api/categories        → liste paginée et filtrable (?search=)
 *   GET    /api/categories/{id}   → une catégorie
 *   POST   /api/categories        → créer        (201, ADMIN)
 *   PUT    /api/categories/{id}   → mettre à jour (ADMIN)
 *   DELETE /api/categories/{id}   → supprimer     (ADMIN)
 *
 * Recherche (nom/slug/description), tri (?sort=name,asc) et pagination
 * (?page=0&size=20) gérés côté base de données.
 *
 * Autorisation (étape 6.25) : le référentiel des catégories est structurant —
 * seule l'ADMINISTRATION (rôle ADMIN) peut le modifier (@PreAuthorize).
 * Lectures : publiques.
 *
 * Aucune logique métier ici : on valide l'entrée (@Valid),
 * on délègue au service, on enveloppe la réponse (ApiResponse).
 */
@Tag(name = "Catégories", description = "Référentiel structurant : lecture PUBLIC, "
        + "modifications ADMIN uniquement.")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Lister les catégories",
            description = "PUBLIC. Sans `search` : liste mise en cache. Avec `search` : "
                    + "recherche nom/slug/description. Tri par `name` par défaut "
                    + "(`?sort=name,asc`).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `CategoryDto` (`id, name, slug, description`)")
    })
    @GetMapping
    public ApiResponse<ApiPageResponse<CategoryDto>> getAll(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        // Sans recherche → liste publique mise en cache (lecture rapide).
        // Avec recherche → requête en base (résultat trop variable pour le cache).
        ApiPageResponse<CategoryDto> page = search == null
                ? categoryService.getPublicList(pageable)
                : categoryService.search(search, pageable);
        return ApiResponse.success("Liste des catégories", page);
    }

    @Operation(summary = "Lire une catégorie", description = "PUBLIC.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CategoryDto`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{id}")
    public ApiResponse<CategoryDto> getById(@PathVariable Long id) {
        return ApiResponse.success(categoryService.getById(id));
    }

    @Operation(summary = "Créer une catégorie",
            description = "ADMIN uniquement. Le slug est dérivé du nom (`\"Jeux Vidéo\" → \"jeux-video\"`).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(name = "Catégorie",
                            value = "{\"name\": \"Jeux Vidéo\", \"description\": \"Gaming et esport\"}"))))
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Catégorie créée"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Nom ou slug déjà utilisé → `CONFLICT`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryDto>> create(@Valid @RequestBody CategoryRequest request) {
        ApiResponse<CategoryDto> body = ApiResponse.success("Catégorie créée", categoryService.create(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Mettre à jour une catégorie", description = "ADMIN uniquement.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`CategoryDto` mise à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`VALIDATION_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<CategoryDto> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success("Catégorie mise à jour", categoryService.update(id, request));
    }

    @Operation(summary = "Supprimer une catégorie", description = "ADMIN uniquement.")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Catégorie supprimée (`data: null`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Non ADMIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.success("Catégorie supprimée", null);
    }
}