package com.streamingafrique.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.streamingafrique.backend.dto.ApiError;
import com.streamingafrique.backend.media.storage.StorageException;

/**
 * Point unique de transformation des exceptions en réponses JSON uniformes.
 * Appliqué à tous les contrôleurs grâce à {@code @RestControllerAdvice}.
 *
 * Principes :
 *  - chaque erreur → { success:false, status, errorCode, message, path, timestamp }
 *  - le code HTTP de l'en-tête est toujours cohérent avec le corps
 *  - AUCUNE stack trace n'est jamais renvoyée au client (même en développement)
 *  - chaque erreur est journalisée en interne (trace complète dans les logs)
 *
 * Suivi des erreurs (étape 6.28) : chaque ligne de log porte la méthode HTTP,
 * le chemin et l'identifiant de requête (MDC {@code requestId}, ajouté au
 * motif Logback) — de quoi retrouver toute l'histoire d'un incident en un
 * grep. Niveaux : WARN pour les erreurs client (4xx, comportement normal à
 * surveiller), ERROR avec pile pour les fautes serveur (5xx, à corriger).
 *
 * Règle d'or (jamais de secret) : on ne journalise ICI que des noms de
 * champs, des chemins et des messages d'exception — JAMAIS de corps de
 * requête, de mot de passe, de token JWT ni d'en-tête Authorization. Le login
 * en échec ne révèle même pas si c'est l'email ou le mot de passe qui est
 * en cause (ni au client, ni dans les logs).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** « POST /api/users » — ce que chaque ligne de log commence par indiquer. */
    private static String where(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

    /** Erreur métier levée volontairement par un service (ApiException.*). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        log.warn("Erreur métier [{}] sur {} : {}", ex.getErrorCode(), where(request), ex.getMessage());
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    /** Validation de corps de requête (@Valid sur un DTO) → 400.
     *  La réponse contient la liste de TOUS les champs invalides. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Valeur invalide",
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        log.warn("Validation échouée sur {} : {}", where(request), errors);
        return buildWithErrors(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation échouée", errors, request);
    }

    /** Mauvais type de paramètre : {id}=abc alors qu'un Long est attendu → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Paramètre '" + ex.getName() + "' invalide : '" + ex.getValue() + "'";
        log.warn("Paramètre invalide sur {} : {}", where(request), message);
        return build(HttpStatus.BAD_REQUEST, "BAD_PARAM", message, request);
    }

    /** Paramètre de requête requis et absent (ex. ?q=...) → 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Paramètre requis manquant : '" + ex.getParameterName() + "'";
        log.warn("Paramètre manquant sur {} : {}", where(request), message);
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAM", message, request);
    }

    /**
     * Tri invalide : ?sort=proprieteInexistante → 400. Levée par Spring Data
     * quand la propriété de tri n'existe pas sur l'entité requêtée.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleInvalidSort(PropertyReferenceException ex, HttpServletRequest request) {
        String message = "Tri invalide : la propriété '" + ex.getPropertyName() + "' n'existe pas";
        log.warn("Tri invalide sur {} : {}", where(request), message);
        return build(HttpStatus.BAD_REQUEST, "SORT_INVALID", message, request);
    }

    /** Méthode HTTP non autorisée sur la route (ex. DELETE sur une route GET) → 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String message = "Méthode HTTP non supportée : " + ex.getMethod();
        log.warn("Méthode non supportée sur {} : {}", where(request), message);
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", message, request);
    }

    /** Fichier téléversé plus grand que max-file-size (servlet multipart) → 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload trop volumineux sur {} : {}", where(request), ex.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "Fichier trop volumineux (taille maximale autorisée dépassée)", request);
    }

    /** Partie multipart obligatoire absente (upload sans le paramètre "file") → 400. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        log.warn("Partie multipart manquante sur {} : {}", where(request), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MISSING_PART",
                "Partie de fichier manquante : '" + ex.getRequestPartName() + "'", request);
    }

    /** Autre anomalie multipart (multipart non valide) → 400. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException ex, HttpServletRequest request) {
        log.warn("Requête multipart invalide sur {} : {}", where(request), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MULTIPART_INVALID",
                "Requête multipart invalide (le fichier n'a pas pu être lu)", request);
    }

    /** Échec d'une opération de stockage (disque, futur objet) → 500, sans détail. */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiError> handleStorage(StorageException ex, HttpServletRequest request) {
        log.error("Erreur de stockage sur {}", where(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                "Erreur interne de stockage de fichier", request);
    }

    /** Corps JSON illisible / mal formé → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("JSON illisible sur {} : {}", where(request), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "MESSAGE_NOT_READABLE", "Format JSON de la requête invalide", request);
    }

    /** Route inconnue → 404 en JSON uniforme (au lieu de la page Spring). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Ressource introuvable : {}", where(request));
        return build(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Ressource introuvable : /" + ex.getResourcePath(), request);
    }

    /**
     * Autorisation refusée par @PreAuthorize (sécurité par méthode) → 403.
     * Distinction avec la 401 : ici l'utilisateur EST authentifié mais son
     * rôle/authority ne lui permet pas d'accéder (ex. USER sur une route ADMIN).
     * L'équivalent au niveau FILTRE (URL) est traité par ApiSecurityEntryPoint.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Accès refusé (autorisation) sur {} : {}", where(request), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Accès refusé", request);
    }

    /**
     * Méthode protégée appelée sans authentification (manque le principal) → 401.
     */
    @ExceptionHandler(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiError> handleMissingAuthentication(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException ex, HttpServletRequest request) {
        log.warn("Authentification requise sur {} : {}", where(request), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise", request);
    }

    /**
     * Échec d'authentification lors du login (6.24) : email inconnu, mot de passe
     * erroné, compte désactivé… Lève 401 SANS jamais révéler lequel des deux
     * (email ou mot de passe) est en cause — friction pour un attaquant.
     */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationFailed(
            org.springframework.security.core.AuthenticationException ex, HttpServletRequest request) {
        log.warn("Échec d'authentification sur {} : {}", where(request), ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Email ou mot de passe incorrect", request);
    }

    /**
     * Filet de sécurité : toute erreur imprévue → 500.
     * La trace complète va UNIQUEMENT dans les logs du serveur,
     * jamais dans la réponse.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erreur non gérée sur {}", where(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erreur interne du serveur", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String errorCode, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status, errorCode, message, request.getRequestURI()));
    }

    private ResponseEntity<ApiError> buildWithErrors(HttpStatus status, String errorCode, String message,
                                                     Map<String, String> errors, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiError.ofValidation(status, errorCode, message, errors, request.getRequestURI()));
    }
}
