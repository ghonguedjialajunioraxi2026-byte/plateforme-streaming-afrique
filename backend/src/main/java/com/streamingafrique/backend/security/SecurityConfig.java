package com.streamingafrique.backend.security;

import com.streamingafrique.backend.ratelimit.RateLimitingFilter;
import com.streamingafrique.backend.security.jwt.JwtAuthenticationFilter;
import com.streamingafrique.backend.security.jwt.JwtService;
import com.streamingafrique.backend.user.repository.UserRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Socle Spring Security (étapes 6.23 + 6.24) — près du langage naturel de la
 * sécurité : "QUI es-tu, et AS-TU le droit ?"
 *
 * AUTHENTICATION (qui es-tu ?) : chaque requête HTTP passe par la
 * {@link SecurityFilterChain}. Deux transporteurs cohabitent (migration douce) :
 *   - JWT (nouveau, 6.24) : {@link JwtAuthenticationFilter} résout l'en-tête
 *     {@code Authorization: Bearer <jeton>}. C'est le mode privilégié —
 *     stateless, sans appel à la base à chaque requête tant que le jeton est valide ;
 *   - Basic (conservé, 6.23) : email:mot de passe résolu par le
 *     {@link UserDetailsServiceImpl} branché en base (rétro-compatibilité des tests).
 * Session sans état (API) : <b>rien n'est mémorisé entre deux requêtes</b>,
 * chaque requête est authentifiée indépendamment.
 *
 * AUTHORIZATION (as-tu le droit ?) : après authentification, chaque route
 * compare les authorities de l'utilisateur au besoin de la route.
 * Les rôles sont modélisés par {@link com.streamingafrique.backend.user.model.User.Role}
 * et mappés en authorities "ROLE_USER", "ROLE_CREATOR", "ROLE_MODERATOR",
 * "ROLE_ADMIN" par UserDetailsServiceImpl (JWT porte le même claim "roles").
 * La HIÉRARCHIE (bean {@link RoleHierarchy}) fait qu'un MODERATOR « possède »
 * aussi les droits CREATOR et USER, un ADMIN tous les droits.
 * La vérification fine se fait :
 *   - au niveau HTTP (authorizeHttpRequests) pour la politique globale ;
 *   - au niveau méthode ({@code @PreAuthorize}) sur les contrôleurs pour les
 *     règles de rôle, complétées par les gardes de PROPRIÉTÉ
 *     ({@link CurrentUser}) dans les services — depuis l'étape 6.25, les
 *     actions sont liées à l'identité AUTHENTIFIÉE, plus jamais d'identité
 *     « déclarative » dans le payload (cf. SecurityDemoController).
 *
 * FILTRE vs CONTRÔLEUR : la chaîne de filtres s'exécute AVANT le DispatcherServlet ;
 * un point d'entrée jamais atteint (401/403) ne passe PAS par un @RestController.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiSecurityEntryPoint apiSecurityEntryPoint;

    public SecurityConfig(ApiSecurityEntryPoint apiSecurityEntryPoint) {
        this.apiSecurityEntryPoint = apiSecurityEntryPoint;
    }

    /**
     * Surface PUBLIQUE actuelle — liste blanche EXPLICITE : ces routes sont
     * volontairement accessibles sans authentification.
     *  - /api/health, /api/info       : monitoring et méta-données ;
     *  - /ws (+ fallback)             : handshake WebSocket/STOMP (la présence
 *    s'appuie sur l'en-tête userId, pas encore sur un token) ;
 *  - contenu en lecture           : catalogue (vidéos, clips, créateurs,
 *    catégories, streams, recherche), follows, présence ;
 *    (étape 22 : notifications et events/publish exigent un authentifié) ;
     *  - /api/users                   : INSCRIPTION (POST) et lecture publiques ;
     *    les actions d'ÉCRITURE (profil/rôle/suppression) sont verrouillées par
     *    @PreAuthorize dans le contrôleur (propriétaire ou ADMIN) ;
     *  - /api/uploads/**, /api/test/** : démo (upload d'images, erreurs).
     *
     * ⚠️ Tout ce qui n'est PAS listé est refusé (anyRequest().authenticated()) :
     * les nouvelles routes sensibles sont protégées PAR DÉFAUT. Depuis 6.25,
     * les écritures du contenu (streams, vidéos, clips, suivis, notifications,
     * catégories) sont en PLUS verrouillées par rôle/propriété (@PreAuthorize
     * + gardes CurrentUser) ; les lectures restent publiques (consultation).
     */
    /**
     * PUBLIC GET-only matchers (étape 6.26) : seules les LECTURES (GET) sur ces
     * chemins sont accessibles sans authentification. Les écritures (POST, PUT,
     * PATCH, DELETE) tombent dans la règle "authentifié" → un anonyme reçoit
     * <b>401</b> au filtre, un authentifié sans le bon rôle reçoit <b>403</b>
     * via {@code @PreAuthorize} (matrice {@link EndpointTier}).
     */
    private static final String[] PUBLIC_GET_MATCHERS = {
            // Monitoring / infra
            "/api/health",
            "/api/info",
            // WebSocket + STOMP (handshake HTTP)
            "/ws",
            "/ws/**",
            // Contenu public (lecture seule)
            "/api/categories/**",
            "/api/videos/**",
            "/api/clips/**",
            "/api/creators/**",
            "/api/streams/**",
            "/api/search/**",
            "/api/follows/**",
            // Étape 22 : /api/notifications/** et /api/events/** SORTIS de la
            // liste publique — SEC-001 (notifications = données privées,
            // destinataire ou ADMIN) et SEC-003 (POST /api/events/publish =
            // authentifié ; aucun GET n'existe sous /api/events). Un anonyme
            // reçoit 401 au filtre (cohérent avec le reste de l'API).
            "/api/presence/**",
            "/api/users/**",
            // Référentiels Africa+ (étape 19 : lecture publique, jamais de secret)
            "/api/countries/**",
            "/api/locales",
            "/api/currencies",
            "/api/payment-methods",
            "/api/config/region",
            "/api/discovery/**",
            // Profils publics (étape 09 : liste blanche stricte, jamais de secret)
            "/api/profiles/username/**",
            // Démo (lecture erreurs métier)
            "/api/test/**",
    };

    /**
     * Écritures PUBLIQUES (étapes 6.26 + 08) : inscription, connexion et flux
     * d'authentification portant leur propre preuve (refresh, tokens email).
     * Le logout est public : le refresh fourni EST la preuve (un access expiré
     * ne doit pas empêcher de se déconnecter). Restent AUTHENTIFIÉS : me,
     * sessions, change-password (Bearer exigé) et tout le reste.
     */
    private static final String[] PUBLIC_POST_MATCHERS = {
            "/api/auth/login",                // POST connexion (paire de jetons JWT)
            "/api/auth/google",               // POST connexion Google (id_token vérifié = preuve)
            "/api/auth/register",             // POST inscription (rôle USER forcé)
            "/api/auth/refresh",              // POST rotation (refresh valide exigé)
            "/api/auth/logout",               // POST révocation (refresh fourni = preuve)
            "/api/auth/verify-email",         // POST vérification (token à usage unique)
            "/api/auth/resend-verification",  // POST renvoi (réponse générique)
            "/api/auth/forgot-password",      // POST récupération (réponse générique)
            "/api/auth/reset-password",       // POST réinitialisation (token à usage unique)
            "/api/users",                     // POST inscription (legacy, inchangé)
            "/api/ingest/on-publish",        // POST hook serveur média (étape 23 :
                                               // PUBLIC car le SECRET PARTAGÉ ?secret= est la preuve,
                                               // comme les webhooks HMAC ; sans secret valide → 401 ;
                                               // réseau interne uniquement via le reverse proxy)
            "/api/payments/webhooks/**",      // POST webhooks prestataires (étape 14 :
                                              // PUBLIC car la SIGNATURE HMAC est la preuve ;
                                              // sans signature valide → 401, rien persisté)
            "/api/videos/*/view",             // POST vues VOD (étape 15 : PUBLIC, un viewer
                                              // = une vue par fenêtre Redis ; abus coupé en 429)
            "/api/clips/*/view",              // POST vues clips (même garde anti-abus)
    };

    /**
     * Surfaces de DÉMO toujours publiques (étape 6.26, matrice PUBLIC) : elles
     * ne font rien de sensible (déclencher des erreurs de démo) et leurs tests
     * les appellent sans authentification.
     *
     * Étape 22 / SEC-003 : {@code /api/events/**} en est SORTI — POST
     * {@code /api/events/publish} injecte des événements diffusés à tous les
     * abonnés WebSocket (usurpation d'auteur possible en anonyme) : il exige
     * désormais un compte authentifié. Aucun GET n'existe sous /api/events.
     */
    private static final String[] PUBLIC_DEMO_MATCHERS = {
            "/api/test/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RateLimitingFilter rateLimitingFilter) throws Exception {
        http
                // API sans état : pas de cookie de session, CSRF inutile (JWT/Basic).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Authentification par en-tête Authorization: Basic base64(email:password)
                // (rétro-compatibilité 6.23 ; le mode privilégié est le JWT Bearer).
                .httpBasic(Customizer.withDefaults())
                // Politique d'autorisation : liste blanche explicite, tout le reste refusé.
                // Preflight CORS (OPTIONS) = toujours libre (le navigateur vérifie,
                // jamais d'authentification sur un preflight) ; lectures (GET) sur
                // contenu = public ; inscription + login (POST) + démos = public ;
                // toute autre écriture = authentifié (puis @PreAuthorize).
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Monitoring (6.28, étendu 23) : sondes et infos publiques,
                        // métriques Prometheus publiques en LECTURE (données
                        // opérationnelles sans PII : JVM, compteurs HTTP — le
                        // reverse proxy public ne route JAMAIS /actuator/**,
                        // scrape réservé au réseau interne), le reste réservé
                        // aux comptes authentifiés.
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        // Documentation OpenAPI (6.30) : spécification + interface
                        // web publiques (elles ne décrivent que la FORME de l'API,
                        // aucune donnée). Les routes métier restent protégées.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        // Ma chaîne (étape 09) : AUTHENTICATED via JWT — placée AVANT
                        // le matcher public /api/creators/** (première règle gagne).
                        .requestMatchers(HttpMethod.GET, "/api/creators/me").authenticated()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_MATCHERS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_MATCHERS).permitAll()
                        .requestMatchers(PUBLIC_DEMO_MATCHERS).permitAll()
                        .requestMatchers("/api/secure/**", "/api/creator/**",
                                "/api/moderator/**", "/api/admin/**").authenticated()
                        .anyRequest().authenticated())
                // Authentification JWT : résout "Authorization: Bearer <token>"
                // AVANT les mécanismes de login/point d'entrée standards.
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                // Anti-abus (étape 6.27) : APRÈS l'authentification (clé par
                // compte quand possible) mais AVANT l'autorisation — un abus
                // est coupé en 429 avant le contrôle 401/403.
                .addFilterAfter(rateLimitingFilter,
                        UsernamePasswordAuthenticationFilter.class)
                // Réponses d'erreur en JSON uniforme (mêmes conventions que le reste).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiSecurityEntryPoint)
                        .accessDeniedHandler(apiSecurityEntryPoint));
        return http.build();
    }

    /**
     * Filtre JWT exposé en BEAN ici (et non en composant scanné) : il dépend du
     * {@code UserDetailsService}, lui aussi défini dans cette classe — l'injection
     * par méthode évite le cycle de construction entre SecurityConfig et le filtre.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
                                                           UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    /**
     * Source d'authentification = BASE DE DONNÉES (pas de comptes en mémoire).
     * Provider Dao branché sur le UserDetailsServiceImpl + BCrypt : c'est lui
     * que l'{@link AuthenticationManager} consulte lors du login.
     */
    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /** Le point d'entrée de l'authentification programmatique (AuthService.login). */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /** Hachage des mots de passe (BCrypt). Aucun secret en clair en base. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HIÉRARCHIE des rôles (étape 6.25) : USER < CREATOR < MODERATOR < ADMIN.
     * Un rôle « possède » tous les rôles inférieurs : un MODERATOR satisfait
     * {@code hasRole('CREATOR')}, un ADMIN satisfait tout. Les vérifications
     * par rôle restent POSÉES sur le rôle le plus bas permettant l'action
     * (ex. {@code hasRole('CREATOR')} pour publier un stream).
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("MODERATOR")
                .role("MODERATOR").implies("CREATOR")
                .role("CREATOR").implies("USER")
                .build();
    }

    /**
     * Branche la hiérarchie sur l'évaluation des {@code @PreAuthorize} :
     * sans ce bean, {@code hasRole('CREATOR')} serait refusé à un MODERATOR.
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    /**
     * Source d'authentification = BASE DE DONNÉES (pas de comptes en mémoire).
     * Le bean injecte le UserDetailsServiceImpl via UserRepository.
     */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return new UserDetailsServiceImpl(userRepository);
    }
}