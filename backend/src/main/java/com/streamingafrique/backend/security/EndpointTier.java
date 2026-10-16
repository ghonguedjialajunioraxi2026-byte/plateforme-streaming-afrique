package com.streamingafrique.backend.security;

/**
 * MATRICE D'AUTORISATIONS (étape 6.26) — source de vérité unique.
 *
 * <p>Chaque endpoint du backend appartient à UN niveau d'accès. Les niveaux
 * sont hiérarchiques : un niveau supérieur hérite des droits du niveau
 * inférieur (implémenté par le bean {@code RoleHierarchy} de
 * {@link SecurityConfig}).</p>
 *
 * <pre>
 *   PUBLIC          aucune authentification requise (lecture, inscription, login, démo)
 *      │
 *   AUTHENTICATED   tout compte connecté (USER, CREATOR, MODERATOR, ADMIN)
 *      │
 *   CREATOR         création/publication de CONTENU (chaînes, streams, vidéos, clips)
 *      │
 *   MODERATOR       MODÉRATION du contenu d'autrui (visibilité, suppression)
 *      │
 *   ADMIN           ADMINISTRATION de la plateforme (catégories, rôles, comptes)
 * </pre>
 *
 * <p><b>Application.</b> Le niveau PUBLIC est posé au niveau du FILTRE
 * ({@code SecurityConfig#PUBLIC_MATCHERS}) : tout ce qui n'est pas public exige
 * d'être authentifié, donc un anonyme reçoit <b>401</b>. Les niveaux
 * AUTHENTICATED / CREATOR / MODERATOR / ADMIN sont posés au niveau MÉTHODE via
 * {@code @PreAuthorize} sur les contrôleurs, avec les constantes ci-dessous.
 * Un compte connecté mais au niveau insuffisant reçoit <b>403</b>.</p>
 *
 * <p><b>401 vs 403.</b> Non authentifié → 401 UNAUTHORIZED ; authentifié mais
 * non autorisé → 403 FORBIDDEN. Cette distinction est garantie par
 * {@code GlobalExceptionHandler} (qui détecte l'authentification anonyme) et,
 * pour le filtre, par {@link ApiSecurityEntryPoint}.</p>
 *
 * <p><b>Sécurité côté serveur uniquement.</b> Le frontend n'est jamais une
 * barrière de sécurité : chaque requête est revalidée ici, quel que soit le
 * client (web, mobile, curl).</p>
 *
 * <p><b>Répartition par contrôleur.</b></p>
 * <ul>
 *   <li>PUBLIC — GET de lecture sur tous les contrôleurs de contenu, POST
 *       /api/users (inscription), POST /api/auth/login, /api/health, /api/info,
 *       /api/stats/*, /ws/** ; surface de démo : /api/test/** ; crochet
 *       machine-à-machine /api/ingest/on-publish (preuve = secret partagé,
 *       étape 23).</li>
 *   <li>AUTHENTICATED — PUT /api/users/{id} (profil), POST /api/creators
 *       (ouvrir sa chaîne → promotion CREATOR), POST+DELETE /api/follows,
 *       GET /api/notifications et /api/notifications/unread-count (destinataire
 *       ou ADMIN, SEC-001), PATCH /api/notifications/*, POST
 *       /api/events/publish (anti-usurpation, SEC-003),
 *       POST /api/uploads/images, POST+GET /api/beta/feedback* (bêta 24).</li>
 *   <li>CREATOR — POST/PUT/DELETE /api/streams, /api/videos, /api/clips,
 *       PUT/DELETE /api/creators/{id} (contenu et chaînes).</li>
 *   <li>MODERATOR — la modération reste portée par les gardes de service
 *       ({@link CurrentUser#requireOwnerOrModerator}) sur les routes CREATOR
 *       (ex. visibilité d'une vidéo, suppression d'un clip d'autrui).</li>
 *   <li>ADMIN — POST/PUT/DELETE /api/categories, PUT /api/users/{id}/role,
 *       DELETE /api/users/{id}, /api/admin/beta/** (invitations, retours, stats).</li>
 * </ul>
 */
public final class EndpointTier {

    /** PUBLIC — aucune contrainte de rôle, géré au filtre (non utilisé en annotation). */
    public static final String PUBLIC = "permitAll()";

    /** AUTHENTICATED — tout compte connecté (exclut explicitement l'anonyme). */
    public static final String AUTHENTICATED = "isAuthenticated()";

    /** CREATOR — création/publication de contenu (implique AUTHENTICATED). */
    public static final String CREATOR = "hasRole('CREATOR')";

    /** MODERATOR — modération (implique CREATOR, donc AUTHENTICATED). */
    public static final String MODERATOR = "hasRole('MODERATOR')";

    /** ADMIN — administration (implique MODERATOR, donc CREATOR et AUTHENTICATED). */
    public static final String ADMIN = "hasRole('ADMIN')";

    private EndpointTier() {
        // classe de constantes : non instanciable
    }
}
