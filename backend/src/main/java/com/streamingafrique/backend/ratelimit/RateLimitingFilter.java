package com.streamingafrique.backend.ratelimit;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamingafrique.backend.dto.ApiError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Anti-abus HTTP (étape 6.27) : chaque requête {@code /api/**} consomme UN
 * jeton du bucket de sa famille de routes ; bucket épuisé → <b>429</b>.
 *
 * Familles (limites TOUTES différentes, configurables via
 * {@code app.rate-limit.*}) :
 * <pre>
 *   login      POST /api/auth/login, /api/auth/refresh, /api/auth/google
 *              (identifiant = IP)
 *   register   POST /api/users, /api/auth/register, /api/auth/forgot-password,
 *              /api/auth/reset-password, /api/auth/verify-email,
 *              /api/auth/resend-verification (identifiant = IP)
 *   search     GET *?search=...            (identifiant = IP)
 *   messages   POST /api/events/publish    (identifiant = compte, sinon IP)
 *   public     autres GET /api/**          (identifiant = IP)
 *   default    tout le reste               (identifiant = compte, sinon IP)
 * </pre>
 *
 * Étape 08 : les flux sensibles SANS session (inscription, récupération,
 * vérification, rotation) partagent les buckets STRICTS login/register
 * (par IP) — pas de nouveau bucket, pas de nouvelle dépendance (ni Redis
 * supplémentaire, ni Bucket4j) : l'anti force-brute est immédiat.
 *
 * Position dans la chaîne : APRÈS l'authentification (pour cléner par compte
 * quand c'est possible) mais AVANT l'autorisation — un abus est coupé en 429
 * avant même le contrôle 401/403. Les sondes ({@code /api/health},
 * {@code /api/info}), le preflight CORS et tout ce qui n'est pas
 * {@code /api/**} sont ignorés.
 *
 * Sécurité côté serveur uniquement : le client ne peut ni voir ni modifier
 * les compteurs (Redis, jamais le frontend).
 *
 * Suivi des erreurs (étape 6.28) : chaque rejet 429 est journalisé en WARN
 * (famille de bucket + méthode + chemin, MDC {@code requestId} inclus) —
 * les rejets naissent ici, avant le DispatcherServlet, donc hors
 * GlobalExceptionHandler. On ne journalise PAS l'identifiant (email ou IP) :
 * inutile au diagnostic du dépassement, et donnée personnelle à préserver.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    static final String BUCKET_LOGIN = "login";
    static final String BUCKET_REGISTER = "register";
    static final String BUCKET_SEARCH = "search";
    static final String BUCKET_MESSAGES = "messages";
    static final String BUCKET_PUBLIC = "public";
    static final String BUCKET_DEFAULT = "default";

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimitService rateLimitService,
                              RateLimitProperties properties,
                              ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        Bucket bucket = resolve(path, method, request);
        if (bucket == null || !properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitProperties.Bucket limit = switch (bucket.name) {
            case BUCKET_LOGIN -> properties.getLogin();
            case BUCKET_REGISTER -> properties.getRegister();
            case BUCKET_SEARCH -> properties.getSearch();
            case BUCKET_MESSAGES -> properties.getMessages();
            case BUCKET_PUBLIC -> properties.getPublicBucket();
            default -> properties.getDefaultBucket();
        };

        String identifier = bucket.perUser ? userOrIp(request) : clientIp(request);
        RateLimitService.Verdict verdict =
                rateLimitService.tryConsume(bucket.name, identifier, limit.getRequests(), limit.getWindow());

        if (!verdict.allowed()) {
            log.warn("Rate limiting (bucket {}) sur {} {} — 429, réessai dans {}s",
                    bucket.name, request.getMethod(), path, verdict.retryAfterSeconds());
            writeTooManyRequests(request, response, verdict.retryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }

    /** Famille de la requête (null = ignorée : pas une route API limitée). */
    private record Bucket(String name, boolean perUser) {
    }

    private Bucket resolve(String path, String method, HttpServletRequest request) {
        if (!path.startsWith("/api/")) {
            return null;
        }
        // Sondes : jamais limitées (monitoring).
        if (path.equals("/api/health") || path.equals("/api/info")) {
            return null;
        }
        // Connexion + rotation + Google : anti force-brute (le refresh et
        // l'id_token portent un secret, les deviner doit coûter aussi cher
        // qu'un login). /api/auth/google/link reste en bucket default (par
        // compte authentifié) : la session Bearer EST la preuve.
        if (HttpMethod.POST.matches(method)
                && (path.equals("/api/auth/login") || path.equals("/api/auth/refresh")
                        || path.equals("/api/auth/google"))) {
            return new Bucket(BUCKET_LOGIN, false);
        }
        // Inscription et flux sans session (création massive, spam email, spam
        // reset) : bucket STRICT register. POST exact ; PUT/DELETE = default.
        if (HttpMethod.POST.matches(method)
                && (path.equals("/api/users")
                        || path.equals("/api/auth/register")
                        || path.equals("/api/auth/forgot-password")
                        || path.equals("/api/auth/reset-password")
                        || path.equals("/api/auth/verify-email")
                        || path.equals("/api/auth/resend-verification"))) {
            return new Bucket(BUCKET_REGISTER, false);
        }
        // Publication d'événements temps réel (chat, notifications…) : anti spam.
        if (path.equals("/api/events/publish") && HttpMethod.POST.matches(method)) {
            return new Bucket(BUCKET_MESSAGES, true);
        }
        // Recherche : GET avec ?search= (quel que soit le contrôleur).
        if (HttpMethod.GET.matches(method)) {
            String search = request.getParameter("search");
            if (search != null && !search.isBlank()) {
                return new Bucket(BUCKET_SEARCH, false);
            }
            return new Bucket(BUCKET_PUBLIC, false);
        }
        return new Bucket(BUCKET_DEFAULT, true);
    }

    /** Compte authentifié si possible, sinon IP (le login/l'inscription restent à l'IP). */
    private String userOrIp(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return "user:" + authentication.getName();
        }
        return clientIp(request);
    }

    /**
     * IP cliente (SEC-005) : {@code X-Forwarded-For} (premier élément) UNIQUEMENT
     * si {@code app.rate-limit.trust-proxy=true} (proxy de confiance ou tests) ;
     * sinon adresse distante TCP ({@code getRemoteAddr}), non falsifiable.
     * Sans proxy de confiance, un client forgeant l'en-tête contournerait les
     * buckets par IP — d'où la valeur {@code false} recommandée en exposition
     * directe (voir application.yml).
     */
    private String clientIp(HttpServletRequest request) {
        if (properties.isTrustProxy()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + forwarded.split(",")[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        return "ip:" + (remote == null ? "unknown" : remote);
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                      long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(Math.max(1L, retryAfterSeconds)));
        ApiError error = ApiError.of(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Trop de requêtes, réessayez dans quelques instants", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), error);
    }
}
