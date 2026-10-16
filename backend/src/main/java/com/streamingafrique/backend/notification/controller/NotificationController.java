package com.streamingafrique.backend.notification.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiPageResponse;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.notification.dto.NotificationDto;
import com.streamingafrique.backend.notification.service.NotificationService;
import com.streamingafrique.backend.security.CurrentUser;
import com.streamingafrique.backend.security.EndpointTier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller REST des notifications — endpoints :
 *   GET   /api/notifications?recipientId=            → centre paginé (AUTHENTICATED, destinataire ou ADMIN — SEC-001)
 *   GET   /api/notifications/unread-count?recipientId= → badge non-lues (AUTHENTICATED, destinataire ou ADMIN — SEC-001)
 *   PATCH /api/notifications/{id}/read                → marquer UNE notification lue (AUTHENTICATED)
 *   POST  /api/notifications/{recipientId}/read-all   → marquer tout lu (AUTHENTICATED)
 *
 * Autorisation (étape 6.26, durcie 22) : matrice EndpointTier.
 * Lectures : AUTHENTICATED + destinataire validé en service (données privées).
 * Marquages lu : AUTHENTICATED (tout connecté), destinataire validé en service.
 */
@Tag(name = "Notifications", description = "Centre de notifications : lectures "
        + "connecté (destinataire ou ADMIN), marquages « lu » connecté avec destinataire = soi-même.")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "Notifications d'un utilisateur",
            description = "Connecté, DESTINATAIRE ou ADMIN (SEC-001 : données privées, "
                    + "lire celles d'autrui → `403`). Les plus récentes d'abord, paginées "
                    + "(`?recipientId=2&page=0&size=20`). "
                    + "Types : `NEW_FOLLOWER` (nouvel abonné)…")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Page de `NotificationDto` (`id, type, title, message, linkUrl, isRead, createdAt`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Notifications d'un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Destinataire inexistant",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping
    public ApiResponse<ApiPageResponse<NotificationDto>> list(
            @RequestParam Long recipientId, @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        return ApiResponse.success("Notifications",
                notificationService.forUser(recipientId, pageable, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Badge non-lues",
            description = "Connecté, DESTINATAIRE ou ADMIN (SEC-001). Compteur pour la cloche du frontend.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Nombre dans `data`"))
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@RequestParam Long recipientId, Authentication authentication) {
        return ApiResponse.success(
                notificationService.unreadCount(recipientId, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Marquer une notification lue",
            description = "Connecté, DESTINATAIRE uniquement (marquer celle d'autrui → `403`).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "`NotificationDto` (`isRead: true`)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Notification d'un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationDto> markAsRead(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("Notification lue",
                notificationService.markAsRead(id, CurrentUser.from(authentication)));
    }

    @Operation(summary = "Tout marquer lu",
            description = "Connecté, pour SOI-MÊME (`recipientId` = id de l'appelant). "
                    + "Renvoie le nombre restant (0).")
    @SecurityRequirements({@SecurityRequirement(name = "bearerAuth"), @SecurityRequirement(name = "basicAuth")})
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Nombre de non-lues restantes dans `data`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Non authentifié",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "`recipientId` d'un autre utilisateur",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Destinataire inexistant",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PreAuthorize(EndpointTier.AUTHENTICATED)
    @PostMapping("/{recipientId}/read-all")
    public ApiResponse<Integer> markAllAsRead(@PathVariable Long recipientId, Authentication authentication) {
        return ApiResponse.success("Toutes lues",
                notificationService.markAllAsRead(recipientId, CurrentUser.from(authentication)));
    }
}