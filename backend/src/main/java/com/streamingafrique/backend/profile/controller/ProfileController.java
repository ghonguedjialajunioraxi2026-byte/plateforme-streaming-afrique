package com.streamingafrique.backend.profile.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.profile.dto.ProfileDto;
import com.streamingafrique.backend.profile.dto.PublicProfileDto;
import com.streamingafrique.backend.profile.dto.UpdateProfileRequest;
import com.streamingafrique.backend.profile.service.ProfileService;
import com.streamingafrique.backend.security.CurrentUser;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Profil utilisateur (étape 09).
 *
 *   - GET /api/profiles/me : profil COMPLET du connecté (Bearer exigé,
 *     identité lue dans le JWT — jamais de userId frontend) ;
 *   - GET /api/profiles/username/{username} : profil PUBLIC (liste blanche
 *     stricte, 404 si privé/inexistant) ;
 *   - PUT /api/profiles/me : modification partielle (champs présents
 *     uniquement, jamais role/status/password).
 */
@Tag(name = "Profils", description = "Profil connecté (privé) et profils publics.")
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Mon profil complet",
            description = "Profil du compte connecté (Bearer requis) : identité + "
                    + "extension + préférences. Jamais de secret.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Profil complet"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/me")
    public ApiResponse<ProfileDto> me(Authentication authentication) {
        return ApiResponse.success("Mon profil", profileService.getMyProfile(CurrentUser.from(authentication)));
    }

    @Operation(summary = "Profil public par pseudo",
            description = "Liste blanche stricte (jamais d'email, naissance, "
                    + "préférences, secret). 404 si inexistant ou privé.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Profil public"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Profil introuvable ou privé",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/username/{username}")
    public ApiResponse<PublicProfileDto> byUsername(@PathVariable String username) {
        return ApiResponse.success("Profil public", profileService.getPublicProfile(username));
    }

    @Operation(summary = "Modifier mon profil",
            description = "Champs présents uniquement (null = inchangé). Jamais "
                    + "role/status/password (401/403 sinon par construction : "
                    + "ces champs n'existent pas dans la requête).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Profil mis à jour"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation (format, URL, date)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/me")
    public ApiResponse<ProfileDto> updateMe(@Valid @RequestBody UpdateProfileRequest request,
                                            Authentication authentication) {
        return ApiResponse.success("Profil mis à jour",
                profileService.updateMyProfile(CurrentUser.from(authentication), request));
    }

    @Operation(summary = "Changer mon avatar",
            description = "Upload sécurisé (MIME sniffé, 5 Mo, nom UUID) + liaison "
                    + "immédiate. L'ancien fichier stocké est supprimé (jamais une "
                    + "URL externe). Réponse : profil complet.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Avatar remplacé"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Fichier manquant",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
                    description = "Image trop volumineuse",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415",
                    description = "Type non autorisé (contenu réel)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfileDto> updateAvatar(@RequestPart("file") MultipartFile file,
                                                Authentication authentication) {
        return ApiResponse.success("Avatar mis à jour",
                profileService.updateAvatar(CurrentUser.from(authentication), file));
    }
}
