# Démarrer le backend en développement

> Règle d'or (rappelée partout, appliquée partout) : **aucun secret n'est
> commité dans Git** — ni ici, ni dans `application-*.yml`, ni dans l'image
> Docker. Les secrets vivent uniquement dans l'environnement local
> (variables shell ou fichier `.env` git-ignoré) et, un jour, dans
> l'environnement du serveur de production.

## 1. Prérequis

- Java 17, Maven 3.9+, Docker + Docker Compose
- Ports libres : `8080` (API), `5432` (PostgreSQL), `6379` (Redis)

## 2. Démarrer PostgreSQL + Redis (une fois)

**Option A — tout-en-un (recommandée)**, depuis `backend/` :

```bash
docker compose up -d        # postgres:16 + redis:7 + volumes persistants
docker compose ps           # les 2 services doivent être "healthy"
```

**Option B — conteneurs séparés** (équivalent) :

```bash
docker run -d --name streaming-afrique-db \
  -e POSTGRES_DB=streaming_afrique -e POSTGRES_USER=streaming \
  -e POSTGRES_PASSWORD=<mot-de-passe-local> -p 5432:5432 postgres:16-alpine
docker run -d --name streaming-redis -p 6379:6379 redis:7-alpine
```

## 3. Lancer l'API (profil `dev`)

`DB_PASSWORD` est **obligatoire** (aucune valeur par défaut, même en dev).
`JWT_SECRET` est recommandé (≥ 32 caractères, ex. `openssl rand -base64 48`) ;
sans lui, l'API démarre quand même mais avec une clé **éphémère**
(jetons invalides à chaque redémarrage — signalé par un warning).

**Sans fichier `.env` (variables shell) :**

```bash
cd backend
DB_PASSWORD=<mot-de-passe-local> \
JWT_SECRET=$(openssl rand -base64 48) \
  mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Avec fichier `.env` (recommandé pour docker-compose) :**

```bash
cd backend
cp .env.example .env          # puis éditer DB_PASSWORD / JWT_SECRET
# .env est git-ignoré — jamais commité
docker compose up -d --build  # utilise automatiquement .env
# ou en local sans Docker :
set -a; source .env; set +a
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Repère dans les logs : `The following 1 profile is active: "dev"`.

> Détail des variables : voir `backend/.env.example` (modèle exhaustif) et
> `docs/environnements.md` (matrice dev vs prod).

## 4. Vérifier que ça tourne

```bash
curl localhost:8080/api/health          # {"status":"UP", ...}
curl localhost:8080/api/info            # identité de la plateforme
curl localhost:8080/actuator/health     # sondes + détails (dev uniquement)
open http://localhost:8080/swagger-ui.html   # documentation OpenAPI
```

Inscription + connexion d'essai :

```bash
curl -X POST localhost:8080/api/users -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","displayName":"Ada","password":"motdepasse-xyz"}'
curl -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"motdepasse-xyz"}'
# → data.accessToken à présenter en `Authorization: Bearer <jeton>`
```

## 5. Dev vs prod — ce qui change

| Sujet | `dev` (ce poste) | `prod` (serveur, **non déployé à ce stade**) |
|---|---|---|
| Base | `ddl-auto: validate` + **Flyway** (`db/migration/V1__…V15__`, baseline auto si base pré-Flyway), pool 5/2, valeurs `localhost` par défaut | `ddl-auto: none` + **Flyway strict** (sans baseline), pool 20/5, `DB_*` **sans défaut** (démarrage refusé si absent) |
| Redis | `localhost:6379`, sans mot de passe ni TLS | `REDIS_*` **sans défaut**, TLS activé par défaut |
| JWT | `JWT_SECRET` optionnel (éphémère + warning sinon) | `JWT_SECRET` **obligatoire** (démarrage refusé si absent) |
| CORS / WebSocket | Vite local, surchargeable via `CORS_ALLOWED_ORIGINS` | Domaine officiel via `CORS_ALLOWED_ORIGINS` (WS suit la même valeur sauf `WS_ALLOWED_ORIGINS` dédié) |
| Erreurs HTTP | message + pile (`include-*: always`) | jamais de détail (`never`) |
| Logs | console lisible, `com.streamingafrique: DEBUG` | console **JSON** + fichier tournant `logs/backend.log`, niveau INFO, sans secret |
| Monitoring | `health,info,metrics` + détails | `health,info` uniquement, sans détails |
| Fichiers | `./uploads` local | `STORAGE_LOCATION` du serveur (volume persistant) |

Fichiers sources : `backend/src/main/resources/application.yml` (socle commun),
`application-dev.yml`, `application-prod.yml`.

## 6. Passer en production (plus tard — repères, pas un déploiement)

1. Construire l'image : `docker build -t streaming-afrique-backend ./backend`
2. Fournir **toutes** les variables sur le serveur (jamais dans Git) :
   `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`,
   `REDIS_HOST/REDIS_PORT/REDIS_PASSWORD`, `JWT_SECRET` (≥ 32 octets),
   `CORS_ALLOWED_ORIGINS`, `STORAGE_LOCATION` (volume persistant).
3. Créer le schéma **avant** le premier démarrage (`ddl-auto: none` ne crée rien).
4. Vérifier `GET /actuator/health` (public, sans détails) puis le reste authentifié.
