# FLICK — Plateforme Streaming Afrique

> Streaming live + VOD pensé pour l'Afrique : faible bande passante, paiements Mobile Money, multilingue (FR/EN), temps réel.

[![CI](https://github.com/ghonguedjialajunioraxi2026-byte/plateforme-streaming-afrique/actions/workflows/ci.yml/badge.svg)](https://github.com/ghonguedjialajunioraxi2026-byte/plateforme-streaming-afrique/actions/workflows/ci.yml)

## Sommaire

- [Aperçu](#aperçu)
- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Démarrage rapide](#démarrage-rapide)
- [Variables d'environnement](#variables-denvironnement)
- [Scripts utiles](#scripts-utiles)
- [Tests & qualité](#tests--qualité)
- [Déploiement](#déploiement)
- [Sécurité](#sécurité)
- [Documentation](#documentation)
- [Contribuer](#contribuer)
- [Licence](#licence)

## Aperçu

| Brique | Techno | Dossier |
|---|---|---|
| Backend API | Spring Boot 3.4 / Java 17, PostgreSQL, Redis, WebSocket | `backend/` |
| Frontend Web | React 19 + Vite + Tailwind 4, HLS.js, PWA | `frontend-web/` |
| Mobile | Flutter (Android / iOS / Web) | `mobile-app/` |
| Infra | Docker Compose, Nginx, SRS (RTMP→HLS), Prometheus + Grafana | `infra/` |
| Docs | Guides dev, sécu, infra, IA, Afrique | `docs/` |

Fonctionnalités : lives, VOD, clips, chat temps réel, follows, wallets & gifts, abonnements, paiements (sandbox + webhooks idempotents), modération + IA (stub local par défaut), recherche, régions Afrique, beta publique, dashboards créateur / modérateur / admin.

## Architecture

```
                    ┌─────────────┐
                    │   Nginx     │  ← TLS, reverse-proxy
                    └──────┬──────┘
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
  frontend-web        backend API         SRS / HLS
  (React PWA)      (Spring Boot :8080)   (RTMP:1935, HLS:18080)
         │                 │
         │            PostgreSQL + Redis
         ▼                 ▼
  mobile-app (Flutter) ← REST + WebSocket (STOMP)
```

Détails : `docs/infrastructure/ARCHITECTURE.md`, `docs/security/architecture.md`.

## Prérequis

- Java 17 + Maven
- Node 22 + npm
- Flutter 3.x (pour `mobile-app/`)
- Docker + Docker Compose (pour staging/prod)
- FFmpeg (transcodage VOD/live)
- PostgreSQL 16 + Redis 7 (ou via Compose)

## Démarrage rapide

### 1. Cloner + configurer

```bash
git clone https://github.com/ghonguedjialajunioraxi2026-byte/plateforme-streaming-afrique.git
cd plateforme-streaming-afrique
cp .env.example .env   # renseigner les valeurs (ne jamais commiter le .env)
```

### 2. Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# API : http://localhost:8080 | Swagger : http://localhost:8080/swagger-ui.html
```

Voir `docs/demarrer-backend-dev.md`.

### 3. Frontend Web

```bash
cd frontend-web
cp .env.example .env   # VITE_API_URL=http://localhost:8080 ...
npm ci
npm run dev            # http://localhost:5173
```

### 4. Mobile

```bash
cd mobile-app
cp .env.example .env
flutter pub get
flutter run
```

### 5. Stack complète (staging/prod)

```bash
docker compose -f infra/docker-compose.staging.yml up --build
# ou prod :
docker compose -f infra/docker-compose.prod.yml up --build -d
```

Monitoring : `infra/monitoring/docker-compose.monitoring.yml` (Prometheus + Grafana). Voir `docs/infrastructure/MONITORING.md`.

## Variables d'environnement

Modèle complet : `.env.example` (racine) + `frontend-web/.env.example` + `mobile-app/.env.example`.

Principales :

| Clé | Usage |
|---|---|
| `DATABASE_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL |
| `REDIS_HOST`, `REDIS_PORT` | Cache, présence, rate-limit |
| `JWT_SECRET` (≥32 car.), `JWT_*_TTL` | Auth |
| `CORS_ALLOWED_ORIGINS` | Frontend autorisé |
| `AI_ENABLED`, `AI_PROVIDER=stub\|http`, `AI_API_KEY` | IA (clé côté backend uniquement) |
| `PAYMENTS_SANDBOX_ENABLED`, `PAYMENT_WEBHOOK_SECRET` | Paiements |
| `RTMP_INGEST_URL`, `HLS_BASE_URL`, `INGEST_CALLBACK_SECRET` | Streaming |

> Jamais de secret dans le frontend ni dans Git. Voir `SECURITY.md`.

## Scripts utiles

```bash
infra/scripts/smoke.sh              # santé API + frontend
infra/scripts/backup-postgres.sh    # backup PG
infra/scripts/restore-postgres.sh   # restore PG
infra/scripts/reset-dev-users.sh    # reset users dev (dev uniquement)
```

## Tests & qualité

```bash
# Backend
cd backend && mvn test

# Frontend
cd frontend-web && npm run lint && npm test -- --run && npm run build

# Mobile
cd mobile-app && flutter analyze && flutter test
```

CI (`.github/workflows/ci.yml`) : lint + tests + builds frontend/backend + validation Docker/Compose sur `main` et `develop`. Aucun secret, aucun déploiement auto.

## Déploiement

- `docs/infrastructure/DEPLOY.md` — procédure staging → prod
- `docs/infrastructure/ROLLBACK.md` — rollback
- `docs/infrastructure/BACKUP.md` / `RESTORE.md` — sauvegarde / restauration
- `docs/infrastructure/INCIDENTS.md` — runbooks
- `docs/beta/PUBLIC_ROLLOUT.md` + `docs/beta/PROGRAMME.md` — sortie publique

La prod ne part qu'après validation explicite (cf. CI + `DEPLOY.md`).

## Sécurité

- `SECURITY.md` — signalement, scope
- `docs/security/architecture.md` + `docs/security/production.md` — durcissement, JWT, CORS, rate-limit, headers, secrets

Règles d'or : JWT long et aléatoire en prod, CORS restreint, HTTPS partout, webhook signé, moindre privilège DB, logs sans PII.

## Documentation

- `docs/environnements.md` — dev / staging / prod
- `docs/demarrer-backend-dev.md` — backend local
- `docs/ia.md` — IA (stub → OpenAI-compatible)
- `docs/africa.md` — régions, langues, data-saver, Mobile Money
- `docs/infrastructure/*` — archi, monitoring, backup, incidents
- `CHANGELOG.md` — historique

## Contribuer

1. Fork + branche (`feat/...`, `fix/...`)
2. Lint + tests verts
3. PR vers `main` avec description claire + captures si UI
4. CI verte + revue requise

## Licence

MIT — voir [LICENSE](LICENSE).
