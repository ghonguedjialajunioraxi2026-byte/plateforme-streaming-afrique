# FLICK — Architecture de sécurité (étape 22)

## 1. Principes

- **Deny by default** : `SecurityConfig` — liste blanche explicite
  (`PUBLIC_GET/POST/DEMO_MATCHERS`), tout le reste `authenticated()`.
- **Le frontend n'est jamais une barrière** : chaque requête est revalidée
  côté backend (voir `EndpointTier`, source de vérité de la matrice).
- **Identité serveur** : jamais de `userId`/`role` client faisant foi —
  `CurrentUser` extrait de l'`Authentication` + gardes
  (`requireOwnerOrAdmin`, `requireSelf`, …).
- **Secrets hors Git** : `.env` git-ignorés, `.env.example` sans valeurs ;
  profil `prod` en échec rapide si un secret manque.

## 2. Actifs et exigences

| Actif | Conf. | Intégrité | Dispo. | Traçabilité |
|---|---|---|---|---|
| Mots de passe (BCrypt, jamais loggés/retournés) | ✓ | ✓ | — | login OK/KO |
| JWT access (15 min) / refresh (7 j, rotation + révocation) | ✓ | ✓ | — | sessions |
| Emails (masqués en lecture publique, SEC-002) | ✓ | — | — | — |
| Notifications (destinataire/ADMIN, SEC-001) | ✓ | ✓ | — | — |
| Stream keys (SecureRandom, hash SHA-256 stocké, rotation) | ✓ | ✓ | — | rotation |
| Wallet/coins/paiements (prix serveur, idempotence, HMAC webhook) | ✓ | ✓ | — | transactions |
| Rôles (ADMIN exclusif, anti auto-promotion) | ✓ | ✓ | — | audit admin |

## 3. Acteurs et matrice RBAC

Visiteur anonyme < USER < CREATOR < MODERATOR < ADMIN (hiérarchie
`RoleHierarchy`, tests `RolePermissionTest`).

| Zone | ANON | USER | CREATOR | MOD | ADMIN |
|---|---|---|---|---|---|
| Catalogue public (sans email) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Compte (lecture, email masqué) | ✓ | ✓ | ✓ | ✓ | ✓ (+email) |
| Notifications (siennes) | ✗ (401) | ✓ | ✓ | ✓ | ✓ (+support) |
| Création contenu / events/publish | ✗ (401) | ✓* | ✓ | ✓ | ✓ |
| Modération d'autrui | ✗ | ✗ | ✗ | ✓ | ✓ |
| Administration / rôles | ✗ | ✗ | ✗ | ✗ (403) | ✓ |

\* USER peut ouvrir sa chaîne (promotion CREATOR), suivre, chatter connecté.

## 4. Correctifs étape 22

- **SEC-001 (HIGH)** : `GET /api/notifications*` anonymes → 401 ;
  destinataire-ou-ADMIN exigé en service (`requireOwnerOrAdmin`).
- **SEC-002 (MEDIUM)** : email masqué (`null`, champ omis en JSON)
  sur `GET /api/users*` sauf propriétaire/ADMIN.
- **SEC-003 (MEDIUM)** : `POST /api/events/publish` exige un authentifié
  (filtre + `@PreAuthorize`) — fin de l'injection anonyme d'événements
  diffusés. Résiduel documenté : `SEND /app/events/publish` (STOMP démo,
  sans consommateur réel ; le vrai chat applicatif devra porter sa propre
  authentification à sa création).
- **SEC-004 (MEDIUM)** : profil `prod` — `JWT_SECRET` absent ou < 32 octets
  → démarrage refusé (`IllegalStateException`). Dev/tests inchangés.
- **SEC-005 (LOW)** : `app.rate-limit.trust-proxy` — `false` en exposition
  directe (IP TCP réelle, non falsifiable) ; `true` derrière proxy de
  confiance (défaut, compatible tests).

## 5. Risques acceptés (documentés, non corrigés à cette étape)

- **HTTP Basic conservé** : la suite de tests l'utilise massivement
  (rétro-compatibilité 6.23) ; mitigations = HTTPS-only en prod, BCrypt,
  stateless. Réévaluer à la migration 100 % Bearer.
- **Présence WS par `userId` déclaré** : impact limité à l'oracle de
  présence (pas d'accès données) ; à lier au JWT avec le vrai chat.
- **Swagger public en dev/tests** : coupé en prod (`springdoc.enabled=false`).
- **Refresh en `localStorage` (web) / secure storage (mobile)** : standard
  SPA ; rotation + révocation côté serveur.
- **Liste `/api/users` anonyme** : métadonnées sociales (pseudo, avatar)
  sans email — acceptable pour une plateforme sociale.

## 6. Opérations sensibles journalisées (sans secret)

Login OK/KO, logout, refresh rotation, reset password, changement de rôle,
suspension/ban, remboursement, rotation stream key, actions admin —
jamais de mot de passe / JWT / stream key / secret dans les logs.
