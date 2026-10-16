package com.streamingafrique.backend.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre d'authentification JWT (étape 6.24).
 *
 * S'exécute sur CHAQUE requête, une seule fois, AVANT l'accès aux ressources.
 * Il transforme un token d'accès présentable en présence Spring Security :
 *
 *   1. EXTRACTION  : lit {@code Authorization: Bearer <token>} ;
 *   2. VALIDATION  : {@link JwtService#isValidAccessToken} (signature, émetteur,
 *                    type ACCESS, expiration) ;
 *   3. CHARGEMENT  : résout le compte via {@link UserDetailsService} (email du
 *                    jeton, source de vérité = PostgreSQL) — rejet si compte supprimé ;
 *   4. CONTEXTE    : si la requête n'est pas déjà authentifiée, un
 *                    {@link UsernamePasswordAuthenticationToken} (principal +
 *                    authorities du jeton) est posé dans le {@code SecurityContext}.
 *
 * Un jeton ABSENT, expiré, falsifié ou d'un autre type (REFRESH) laisse la
 * requête ANONYME : la {@code SecurityFilterChain} décide ensuite (401 sur les
 * routes protégées, accès libre sur la liste blanche). Aucune exception ne
 * quitte ce filtre — la sécurité se refuse silencieusement, jamais en 500.
 *
 * Exposé en BEAN par {@link SecurityConfig} (pas en composant scanné) : il dépend du
 * {@code UserDetailsService} défini dans cette même classe — l'injection par méthode
 * évite le cycle de construction entre les deux beans.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtService.isValidAccessToken(token)) {
                String email = jwtService.extractUsername(token);
                if (email != null) {
                    try {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        if (!userDetails.isEnabled()) {
                            // Compte suspendu/fermé : jeton encore valide mais compte
                            // inactif → requête anonyme (401 si route protégée).
                            log.debug("Compte JWT désactivé — requête anonyme");
                            SecurityContextHolder.clearContext();
                            filterChain.doFilter(request, response);
                            return;
                        }
                        String roleAuthority = jwtService.extractRoleAuthority(token);
                        if (roleAuthority != null) {
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null,
                                    java.util.List.of(new SimpleGrantedAuthority(roleAuthority)));
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            log.debug("Requête authentifiée via JWT pour {}", email);
                        }
                    } catch (Exception e) {
                        // Compte supprimé ou erreur de chargement : requête anonyme,
                        // la chaîne décide ensuite (401 si route protégée). Jamais de 500 ici.
                        log.debug("Compte JWT introuvable ou illisible — requête anonyme : {}", e.getMessage());
                        SecurityContextHolder.clearContext();
                    }
                }
            } else {
                log.debug("Jeton d'accès invalide ou expiré — requête traitée en anonyme");
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Extrait le jeton Bearer de l'en-tête Authorization, ou {@code null}. */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}