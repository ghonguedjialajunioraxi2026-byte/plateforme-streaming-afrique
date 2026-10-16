package com.streamingafrique.backend.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.dto.ApiError;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Réponses 401/403 au format JSON uniforme de la plateforme (ApiError) —
 * les erreurs de sécurité deviennent ainsi compatibles avec le format
 * { success, status, errorCode, message, path, timestamp } déjà utilisé
 * partout ailleurs (GlobalExceptionHandler).
 *
 * Deux rôles dans un seul composant :
 *   - commence() : AUTHENTICATION refusée (anonyme)   → 401 UNAUTHORIZED ;
 *   - handle()   : AUTORISATION refusée (mauvais rôle) → 403 FORBIDDEN.
 *  * L'ObjectMapper injecté est celui de Spring (module JSR-310 pour LocalDate/
 * Instant), pas un "new ObjectMapper()" sans configuration.
 *
 * Suivi des erreurs (étape 6.28) : ces rejets naissent dans la chaîne de
 * FILTRES, avant le DispatcherServlet — ils ne passent donc jamais par le
 * GlobalExceptionHandler. Chaque rejet est journalisé ici en WARN (méthode +
 * chemin, MDC {@code requestId} inclus via le motif Logback) pour garder une
 * trace diagnostique complète. On ne journalise que le NOM de l'exception,
 * jamais son message détaillé : certains messages de sécurité pourraient
 * recopier des fragments d'en-tête Authorization (jeton) — règle d'or,
 * aucun secret dans les logs.
 */
@Component
public class ApiSecurityEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityEntryPoint.class);

    private final ObjectMapper objectMapper;

    public ApiSecurityEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        log.warn("Accès refusé (authentification) sur {} {} : {}",
                request.getMethod(), request.getRequestURI(), authException.getClass().getSimpleName());
        write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.warn("Accès refusé (autorisation) sur {} {} : {}",
                request.getMethod(), request.getRequestURI(), accessDeniedException.getClass().getSimpleName());
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Accès refusé");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = ApiError.of(status, errorCode, message, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), error);
    }
}