# Environnements — développement vs production (étape 6.32)

> **Aucun secret n'est commité dans Git.** Toutes les valeurs sensibles
> (`DB_PASSWORD`, `JWT_SECRET`, `REDIS_PASSWORD`…) proviennent exclusivement
> de l'environnement (`export`, `docker -e`, fichier `.env` git-ignoré en dev,
> secrets manager en prod). Les fichiers `application-*.yml` ne contiennent que
> des références `${VAR}` avec ou sans défaut local.

## 1. Principe des profils

| Fichier | Rôle |
|---|---|
| `backend/src/main/resources/application.yml` | **Socle commun** : port, Jackson UTC, CORS/Redis/JWT *structure*, rate-limit, présence, WebSocket, Actuator, logs. Aucun secret en dur. |
| `application-dev.yml` | **Développement** : se superpose au socle quand `SPRING_PROFILES_ACTIVE=dev`. Valeurs locales, `ddl-auto: update`, logs DEBUG, `health` détaillé. |
| `application-prod.yml` | **Production (non déployée)** : se superpose quand `SPRING_PROFILES_ACTIVE=prod`. Zéro défaut sensible (`${DB_PASSWORD}` sans `:default` → démarrage refusé si absent), `ddl-auto: none`, logs JSON + fichier, Swagger désactivé. |
| `application.yml` (tests) | Aucun profil actif → Spring Boot utilise le profil `default` : H2 en mémoire (`scope test`), création `create-drop`, Redis mockable/Testcontainers. |

Activation :

```bash
# Dev (local)
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
# ou
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Prod (jar, sur serveur — modèle non déployé)
java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod

# Vérifier dans les logs :
# "The following 1 profile is active: \"dev\""  /  "\"prod\""
```

## 2. Matrice complète des différences

| Axe | `dev` (poste local) | `prod` (serveur — **modèle, non déployé**) |
|---|---|---|
| **Variables d'env** | `${VAR:default-local}` tolérant ; `DB_PASSWORD` obligatoire sans défaut, `JWT_SECRET` optionnel (éphémère + WARN) | **Aucun défaut** pour `DB_*`, `REDIS_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS` → échec rapide si manquant |
| **Database** | `jdbc:postgresql://localhost:5432/streaming_afrique`, `ddl-auto: update`, Hikari 5/2, `show-sql: true` | `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`, `ddl-auto: none` (migrations manuelles), Hikari 20/5 |
| **Redis** | `localhost:6379`, sans password, `ssl: false`, TTL courts | `REDIS_HOST/PORT/PASSWORD` requis, `ssl: true` par défaut, préfixe/TTL via env |
| **CORS / WebSocket** | `http://localhost:5173,http://127.0.0.1:5173`, `WS` suit `CORS` | `CORS_ALLOWED_ORIGINS=https://app.streaming-afrique.com` (env), `WS_ALLOWED_ORIGINS` optionnel pour diverger |
| **Logs** | `logback-spring.xml: !prod` → console lisible `[req=...]`, `com.streamingafrique: DEBUG` | `logback-spring.xml: prod` → console **JSON** (`LogstashEncoder` + `requestId`) + fichier tournant `logs/backend.log` (30j, 100Mo), niveau `INFO` |
| **Fichiers** | `./uploads` local, `max-image-size-mb: 5`, types `jpeg/png/gif/webp` | `STORAGE_LOCATION` env (volume `uploads:/app/uploads`), mêmes limites MIME vérifiées sur contenu binaire |
| **Sécurité** | `server.error.include-*: always` (message + stacktrace au client) ; Basic + JWT | `server.error.include-*: never` ; même chaîne SecurityConfig, mais erreurs génériques côté client |
| **JWT** | `JWT_SECRET` optionnel : clé éphémère + warning si absent (jetons invalides au redémarrage) | `${JWT_SECRET}` **obligatoire**, `JWT_ACCESS_TOKEN_TTL`/`REFRESH` via env, `issuer` via env |
| **Monitoring** | `management.endpoints.web.exposure.include: health,info,metrics`, `health.show-details: always` | `include: health,info` uniquement, `show-details: never` ; `SecurityConfig`: `/actuator/health/**` + `/actuator/info` publics (sondes), `/actuator/**` authentifié |

## 3. Fichiers et sécurité

- `.gitignore` (racine + `backend/.gitignore` + `backend/.dockerignore`) ignore : `.env`, `.env.*`, `application-local.yml`, `logs/`, `uploads/`, `target/`, `node_modules/`, `dist/`.
- `backend/.env.example` : modèle exhaustif git-versionné, **sans secret** — commentaires pour chaque variable, valeurs dev faibles explicitement marquées `dev-only`.
- `infra/docker-compose.prod.yml` : modèle prod sans défaut sensible, ports DB/Redis non exposés publiquement, volume `backend_logs` séparé, `SPRING_PROFILES_ACTIVE=prod`.
- `backend/Dockerfile` : multi-étapes, utilisateur `app` non-root, `ENV SPRING_PROFILES_ACTIVE=prod` par défaut (compose dev le surcharge en `dev`), `HEALTHCHECK` sur `/actuator/health`, aucun `ENV` secret.
- `GlobalExceptionHandler` + `ApiSecurityEntryPoint` : aucune pile ni secret en réponse ; `RequestIdFilter` + `MDC` pour corrélation sans fuite.
- Vérification : `git check-ignore backend/.env` → ignoré ; `grep -r JWT_SECRET backend/src/main/resources` → uniquement `${JWT_SECRET}` ; `docker compose config` en prod sans env → erreur attendue.

## 4. Vérifier que le changement de profil ne casse pas

```bash
# 1. Tests (profil default = H2) : doit passer sans profil
mvn test

# 2. Dev : exige DB_PASSWORD, sinon échec au démarrage
DB_PASSWORD=xxx JWT_SECRET=$(openssl rand -base64 48) mvn spring-boot:run -Dspring-boot.run.profiles=dev
curl localhost:8080/actuator/health  # {"status":"UP"} + details visibles

# 3. Prod sans secrets : doit REFUSER de démarrer (échec rapide voulu)
SPRING_PROFILES_ACTIVE=prod java -jar target/backend-*.jar
# → IllegalArgumentException / placeholder 'DB_PASSWORD' not resolved

# 4. Prod avec secrets : identique au dev mais health sans détails, swagger 404
SPRING_PROFILES_ACTIVE=prod DB_HOST=... DB_PASSWORD=... REDIS_HOST=... JWT_SECRET=... CORS_ALLOWED_ORIGINS=https://app.streaming-afrique.com java -jar target/backend-*.jar
curl localhost:8080/actuator/health  # {"status":"UP"} sans details
curl localhost:8080/v3/api-docs      # 404 (springdoc désactivé en prod)
```

Le backend démarre dans les deux profils avec la même codebase ; seul `application-{dev,prod}.yml` et les variables d'environnement changent. Aucun code métier n'est conditionné par `!prod`/`prod` — seul le formatage des logs (`logback-spring.xml`) et la config externalisée divergent.
