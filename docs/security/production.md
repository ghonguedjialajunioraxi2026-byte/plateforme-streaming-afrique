# FLICK — Checklist sécurité production (étape 22 → 23)

À vérifier avant toute mise en production (étape 23 : infrastructure).

## Socle

- [ ] `SPRING_PROFILES_ACTIVE=prod` (jamais `dev` en prod).
- [ ] Secrets fournis par l'environnement serveur, jamais dans Git :
  `DB_PASSWORD`, `JWT_SECRET` (≥ 32 octets, ex. `openssl rand -base64 48`),
  `REDIS_PASSWORD`, `PAYMENT_WEBHOOK_SECRET`, `CORS_ALLOWED_ORIGINS`.
- [ ] Sans secret → démarrage refusé (fail-fast vérifié : DB, JWT, Redis).

## Réseau et exposition

- [ ] HTTPS + HSTS au reverse proxy ; cookies `Secure` si cookies ajoutés.
- [ ] CORS strict : `CORS_ALLOWED_ORIGINS` = domaine(s) frontend exact(s).
- [ ] `RATE_LIMIT_TRUST_PROXY=false` si exposition directe (sans proxy),
  `true` uniquement derrière un reverse proxy de confiance qui pose
  `X-Forwarded-For`.
- [ ] PostgreSQL / Redis / media server NON exposés publiquement
  (réseau interne ; voir `infra/docker-compose.prod.yml` : Redis avec
  `requirepass`, secrets par environnement).
- [ ] Rate limiting resserré via `RATE_LIMIT_*` (défauts = dev/tests).

## Application

- [ ] Swagger/OpenAPI coupé (déjà `enabled: false` en prod).
- [ ] Erreurs génériques (déjà `include-message/stacktrace: never` en prod).
- [ ] Logs INFO sans secret ; `logs/` hors Git, rotation configurée.
- [ ] Uploads : taille/MIME/sniffing déjà en place ; vérifier quotas disque.
- [ ] Webhooks paiements : signature HMAC exigée, sandbox désactivé
  (`sandbox-enabled: false` en prod).
- [ ] JWT : TTL courts conservés (15 min / 7 j) ; rotation refresh active.

## Données et exploitation

- [ ] Compte DB applicatif au moindre privilège (pas de superuser).
- [ ] Backups DB chiffrés, accès restreint, restauration testée.
- [ ] Comptes ADMIN initiaux forts + MFA si disponible ; revue des rôles.
- [ ] Monitoring : `/actuator/health` + alertes (login KO massifs, 429, 5xx).
- [ ] `npm audit` / dépendances Maven vérifiés à chaque release.

## Restes connus (étape 22)

- HTTP Basic encore actif (tests) → HTTPS obligatoire, migration Bearer à planifier.
- `SEND /app/events/publish` (STOMP démo) sans auth → à sécuriser avec le vrai chat.
- Présence WS par `userId` déclaré → lier au JWT avec le vrai chat.
