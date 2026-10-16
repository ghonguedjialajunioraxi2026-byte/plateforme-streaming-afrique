package com.streamingafrique.backend.config;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Documentation OpenAPI de l'API REST (étape 6.30) — lue par le développeur
 * frontend React et générable en client typé.
 *
 *   - JSON OpenAPI 3 : {@code GET /v3/api-docs} ;
 *   - interface web : {@code GET /swagger-ui.html} (essais directs « Try it out »).
 *
 * Conventions documentées ici (même contrat que le code) :
 *   - enveloppes : succès {@code {success:true, message, data, timestamp}},
 *     erreur {@code {success:false, status, errorCode, message, errors?, path, timestamp}},
 *     pages {@code {content, page, size, totalElements, totalPages}} ;
 *   - authentification : login {@code POST /api/auth/login} → {@code accessToken} ;
 *     le présenter en {@code Authorization: Bearer <jeton>} (schéma {@code bearerAuth},
 *     privilégié, stateless) ou en Basic email:mot de passe (schéma {@code basicAuth},
 *     rétro-compatibilité) ;
 *   - rôles hiérarchiques USER < CREATOR < MODERATOR < ADMIN — chaque opération
 *     protégée indique son niveau dans sa description (matrice {@code EndpointTier}) ;
 *   - pagination {@code ?page=0&size=10&sort=createdAt,desc}, recherche
 *     {@code ?search=}, anti-abus 429 + en-tête {@code Retry-After}, corrélation
 *     {@code X-Request-Id} sur chaque réponse.
 *
 * Détail technique : le paramètre {@link Authentication} injecté dans les
 * contrôleurs est un objet serveur (jamais un paramètre HTTP) — on le masque
 * de la documentation plutôt que de polluer chaque méthode d'un
 * {@code @Parameter(hidden = true)}.
 */
@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Authentication.class);
    }

    @Bean
    public OpenAPI streamingAfriqueOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FLICK — API REST")
                        .version("0.0.1")
                        .description("""
                                API de la plateforme FLICK de streaming en direct (catalogue, directs, \
                                VOD, clips, chaînes, suivis, notifications, présence, uploads).

                                ## Pour le frontend React
                                1. **Base URL locale** : `http://localhost:8080` (le frontend Vite \
                                appelle `http://localhost:8080/api`).
                                2. **Enveloppes** : toute réponse de succès est \
                                `{success:true, message, data, timestamp}` ; toute erreur est \
                                `{success:false, status, errorCode, message, errors?, path, timestamp}` \
                                — le code HTTP de l'en-tête correspond toujours au `status` du corps.
                                3. **Listes** : `{content:[...], page, size, totalElements, totalPages}` \
                                via `?page=0&size=10&sort=createdAt,desc` (taille max 100).
                                4. **Authentification** : `POST /api/auth/login` → récupère \
                                `data.accessToken`, puis en-tête `Authorization: Bearer <jeton>` sur \
                                les routes protégées (bouton « Authorize » ci-dessus, schéma \
                                `bearerAuth`). Le schéma `basicAuth` (email:mot de passe) reste \
                                accepté.
                                5. **Rôles** : `USER < CREATOR < MODERATOR < ADMIN` (héritage : un \
                                ADMIN passe partout). Anonyme → `401`, connecté sans le rôle → `403`.
                                6. **Erreurs courantes** : `400 VALIDATION_ERROR` (détail par champ dans \
                                `errors`), `401 UNAUTHORIZED`, `403 FORBIDDEN`, `404 NOT_FOUND`, \
                                `409 CONFLICT`, `429 RATE_LIMIT_EXCEEDED` (attendre `Retry-After`), \
                                `500 INTERNAL_ERROR`. Aucune pile d'exécution n'est renvoyée.
                                7. **Divers** : recherche `?search=` (insensible à la casse), chaque \
                                réponse porte `X-Request-Id` (à transmettre au support en cas \
                                d'incident), temps réel via WebSocket STOMP sur `/ws` (hors scope \
                                de cette documentation REST).
                                """)
                        .contact(new Contact()
                                .name("Équipe FLICK")))
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Environnement de développement local"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton d'accès remis par POST /api/auth/login "
                                        + "(`data.accessToken`). En-tête : "
                                        + "`Authorization: Bearer <jeton>`."))
                        .addSecuritySchemes("basicAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Email + mot de passe en Basic "
                                        + "(rétro-compatibilité ; le JWT Bearer est privilégié).")));
    }
}
