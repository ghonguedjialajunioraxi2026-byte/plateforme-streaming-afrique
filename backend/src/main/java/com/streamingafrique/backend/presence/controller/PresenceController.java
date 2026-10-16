package com.streamingafrique.backend.presence.controller;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.dto.ApiResponse;
import com.streamingafrique.backend.presence.dto.PresenceOnlineDto;
import com.streamingafrique.backend.presence.dto.UserPresenceDto;
import com.streamingafrique.backend.presence.service.PresenceService;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API REST de la présence utilisateur (lectures).
 *
 * La présence est ÉPHÉMÈRE (Redis + TTL) : ces endpoints ne font que lire
 * l'état courant. Sert les bulles « online » des profils, la liste des
 * antennes d'un direct, les destinataires d'une notification…
 *
 * Rappel : l'écriture ne passe pas par REST, elle est événementielle
 * (connexion/déconnexion WebSocket, voir PresenceWebSocketLifecycle).
 */
@Tag(name = "Présence", description = "Lectures PUBLIC de la présence (éphémère, "
        + "Redis + TTL). L'écriture est événementielle (connexion/déconnexion WebSocket `/ws`), "
        + "pas REST : aucun POST ici. Sert les pastilles « online » et les compteurs du frontend.")
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    /** Statut de présence d'un utilisateur (jamais null : OFFLINE par défaut). */
    @Operation(summary = "Statut de présence d'un utilisateur",
            description = "PUBLIC. `UserPresenceDto` (`userId, status: ONLINE|OFFLINE, lastSeenAt…`). "
                    + "Jamais 404 : un utilisateur sans marqueur est `OFFLINE`.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Statut courant"))
    @GetMapping("/users/{id}")
    public ApiResponse<UserPresenceDto> userStatus(@PathVariable Long id) {
        return ApiResponse.success("Statut de présence", presenceService.userPresence(id));
    }

    /** Utilisateurs en ligne + total. */
    @Operation(summary = "Utilisateurs en ligne",
            description = "PUBLIC. `PresenceOnlineDto` (`total, userIds, measuredAt`).",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Liste des ids en ligne + total"))
    @GetMapping("/online")
    public ApiResponse<PresenceOnlineDto> online() {
        List<Long> ids = presenceService.onlineUserIds();
        return ApiResponse.success("Utilisateurs en ligne",
                new PresenceOnlineDto(ids.size(), ids, Instant.now()));
    }
}