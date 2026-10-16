# FLICK — Journal des versions

## beta-0.2.0 — 2026-09-23 (bêta publique contrôlée, étape 25)

### Ajouts
- Pages légales : `/conditions`, `/confidentialite`, `/regles` + liens
  de pied de page ; consentement CGU obligatoire (case + horodatage
  `users.terms_accepted_at`, migration V26).
- Support (`/support`) et statut temps réel (`/statut`, données
  `/api/health` + `/api/info`, jamais de façade).
- SEO : canonical + Open Graph par page (`VITE_SITE_URL`), `robots.txt`.
- Email transactionnel câblé en prod via environnement (vérification,
  récupération) ; rate limiting et `VITE_SITE_URL` documentés.
- Programme : `docs/beta/PUBLIC_ROLLOUT.md` (phases A→D, GO/NO-GO,
  rapport quotidien, surveillance).

### Corrections
- Aucun bug fonctionnel bloquant relevé par l'audit ; socle inchangé.

### Sécurité
- Protections étapes 22–24 conservées ; SMTP et consentement sans
  secret ni donnée superflue ; actuator/ingest toujours refusés au proxy.

### Problèmes connus
- Lien Maven Central lent (~21 Ko/s) : build Docker backend complet
  non rejoué en conteneur (validation équivalente étape 23).
- `SEND /app/events/publish` (STOMP démo) sans auth — sans
  consommateur réel.
- HTTP Basic conservé (tests) — HTTPS obligatoire.
- `mobile-app/test/widget_test.dart` : template obsolète pré-existant.

## beta-0.1.0 — 2026-09-22 (bêta privée, étape 24)

### Ajouts
- Bêta privée : invitations ADMIN à usage unique liées à l'email
  (`/admin/beta`, code montré une fois, expiration, révocation).
- Inscription sur invitation quand `BETA_INVITE_ONLY=true` (staging/bêta).
- Retours testeurs (`/feedback`, catégories BUG/UX/…, contexte non
  sensible) + triage ADMIN (statuts, priorités P0..P3, actes audités).
- Page publique `/beta` (conditions du programme) + champ code sur
  l'inscription + version `beta-0.1.0` affichée.
- Hook ingest serveur média (`POST /api/ingest/on-publish`, étape 23) :
  secret partagé + statut LIVE + clé de stream (SRS `on_publish`).

### Corrections
- `application.yml` : indentation `rate-limit.login` restaurée.
- Hausse compteurs du test d'intégrité DB (25 migrations, 34 tables).

### Sécurité
- Codes d'invitation stockés en empreinte SHA-256 uniquement.
- Feedback : rapporteur forcé serveur, lecture soi/ADMIN, triage ADMIN.
- Protections étape 22 conservées (JWT, RBAC, rate limiting, CORS).

### Infrastructure
- Compose prod/staging/monitoring validés, nginx + SRS vérifiés,
  backup/restore PostgreSQL testés pour de vrai, CI (lint/test/build/
  docker), runbooks `docs/infrastructure/`.

### Problèmes connus
- `SEND /app/events/publish` (STOMP démo) sans auth — sans consommateur
  réel ; à sécuriser avec le vrai chat.
- HTTP Basic conservé (suite de tests dépendante) — HTTPS obligatoire.
- `mobile-app/test/widget_test.dart` : template compteur obsolète
  (`MyApp` inexistant) — antérieur, non bloquant (suite `flutter test`
  à relancer avec SDK : `auth_controller_test` étendu pour Google).
