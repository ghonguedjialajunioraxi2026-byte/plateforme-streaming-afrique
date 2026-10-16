# Sécurité — FLICK

## Signaler une vulnérabilité

Écrivez au mainteneur du dépôt avec :

1. Environnement concerné (`dev`, `test`, `prod`, version/commit).
2. Description + impact estimé (comptes, données, paiements…).
3. Reproduction pas-à-pas (requêtes, rôles utilisés).
4. Correctif suggéré si vous en avez un.

**Règles** : ne testez que vos propres environnements locaux/de test ;
ne tentez ni exfiltration massive, ni déni de service, ni accès aux
comptes d'autrui ; ne publiez pas la vulnérabilité avant correction
(divulgation coordonnée, délai de 90 jours par défaut).

## Ce qui est déjà en place (étape 22)

Authentification JWT (access court + refresh avec rotation/révocation),
RBAC USER < CREATOR < MODERATOR < ADMIN, IDOR couverts par gardes
`CurrentUser`, validation Bean Validation, rate limiting Redis + 429,
webhooks HMAC idempotents, uploads sniffés et quota-tés, secrets hors Git
avec fail-fast en `prod`, Docker non-root. Détails :
`docs/security/architecture.md`, checklist prod :
`docs/security/production.md`.

## Périmètre sensible prioritaire

Auth/JWT/sessions, rôles et IDOR, wallet/coins/paiements/abonnements,
stream keys et ingest, uploads, WebSocket/chat, secrets et logs.
