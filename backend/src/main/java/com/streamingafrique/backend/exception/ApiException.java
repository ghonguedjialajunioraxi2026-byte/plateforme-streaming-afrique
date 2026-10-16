package com.streamingafrique.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception métier de base : un service la lève quand une règle
 * métier n'est pas respectée (ressource introuvable, conflit...).
 *
 * Le GlobalExceptionHandler la convertit en réponse HTTP + JSON cohérent.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    /** 404 — ressource inexistante. */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    /** 400 — entrée invalide. */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    /** 409 — conflit (ex. : email déjà utilisé). */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    /** 403 — accès refusé. */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    /** 401 — authentification requise ou preuve invalide (ex. signature webhook). */
    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    /** 413 — fichier/requête trop volumineux (upload). */
    public static ApiException payloadTooLarge(String message) {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", message);
    }

    /** 415 — type média non supporté (upload : fichier non autorisé). */
    public static ApiException unsupportedType(String message) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_TYPE", message);
    }
}