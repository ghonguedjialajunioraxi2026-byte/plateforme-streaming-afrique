package com.streamingafrique.backend.video.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.streamingafrique.backend.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Clé anti-abus des vues (étape 15, §29) : un viewer = une vue par fenêtre.
 *
 * Compte connecté → {@code u:<id>} (stable, non personnelle au-delà de l'id
 * déjà connu du serveur) ; anonyme → {@code ip:<sha256>} (l'IP N'EST JAMAIS
 * stockée ni journalisée en clair — seul son hash transite vers Redis avec
 * TTL court). Avec prudence : derrière un NAT/CGN, plusieurs foyers peuvent
 * partager une IP — la fenêtre courte (1 h) borne l'erreur.
 */
public final class ViewerKeys {

    private ViewerKeys() {
    }

    public static String of(HttpServletRequest request, CurrentUser actor) {
        if (actor != null && actor.isAuthenticated()) {
            return "u:" + actor.id();
        }
        return "ip:" + sha256Hex(clientIp(request));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
