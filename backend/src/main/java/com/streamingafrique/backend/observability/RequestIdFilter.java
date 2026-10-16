package com.streamingafrique.backend.observability;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Corrélation des requêtes (étape 6.28) : CHAQUE requête reçoit un identifiant
 * unique ({@code X-Request-Id}), propagé partout.
 *
 * Fonctionnement :
 *   - le client peut fournir son propre {@code X-Request-Id} (tracé de bout
 *     en bout depuis le frontend) — il est répercuté tel quel ;
 *   - sinon un UUID court est généré ;
 *   - l'identifiant voyage dans le MDC Logback ({@code %X{requestId}}) : TOUS
 *     les logs de la requête (contrôleurs, services, sécurité, erreurs) le
 *     portent → on retrouve toute l'histoire d'une requête en un grep ;
 *   - il est renvoyé en en-tête de réponse, Y COMPRIS sur les erreurs
 *     (401/403/404/429/500) : le client peut le transmettre au support.
 *
 * Premier filtre de la chaîne servlet (avant Spring Security) : même les
 * rejets sécurité/rate-limiting portent l'identifiant. Le MDC est TOUJOURS
 * nettoyé (finally) : les threads Tomcat étant réutilisés, un oubli
 * contaminerait la requête suivante.
 *
 * Vie privée : l'identifiant est aléatoire, sans lien avec l'utilisateur.
 *
 * Corrélation sur redispatch ERREUR (étape 6.28) : quand aucun contrôleur ne
 * traite la requête (ex. route inexistante hors GlobalExceptionHandler),
 * le conteneur redispatch vers {@code /error} — et la chaîne Spring Security
 * s'y réexécute (un anonyme y reçoit 401 via ApiSecurityEntryPoint). Par
 * défaut un {@code OncePerRequestFilter} ignore ce redispatch, ce qui
 * laisserait ces logs SANS identifiant. On s'y réexécute donc
 * ({@link #shouldNotFilterErrorDispatch()} → {@code false}) en RÉUTILISANT
 * l'identifiant déjà émis en en-tête de réponse : même requête, même
 * identifiant, du premier filtre jusqu'au dernier log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** Nom de l'en-tête (requête comme réponse) et de la clé MDC. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            // Redispatch ERREUR : la requête d'origine ne portait pas l'en-tête,
            // mais la réponse porte déjà l'identifiant émis au premier passage.
            requestId = response.getHeader(REQUEST_ID_HEADER);
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8)
                    + "-" + Long.toString(System.nanoTime() % 1_000_000, 36);
        }
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
