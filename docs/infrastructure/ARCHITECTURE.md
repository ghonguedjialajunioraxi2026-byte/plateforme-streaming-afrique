# FLICK — Architecture d'infrastructure (étape 23)

## Cible (première production : VPS unique, évolutive)

```
INTERNET (80/443/1935 seuls)
  │
  ▼
proxy (nginx : TLS, routage, gzip, X-Request-Id)
 ├── /      → frontend (nginx statique, build Vite)
 ├── /api/  → backend (Java 17, :8080, non publié)
 ├── /ws/   → backend (STOMP, timeouts longs)
 └── /hls/  → srs:18080 (lecture HLS)
  │
  ├── backend ──┬── postgres:5432 (réseau data interne)
  │             ├── redis:6379 (AUTH, AOF, réseau data interne)
  │             └── srs:1985 (API interne) / OBS → srs:1935 (RTMP, hook on_publish)
  └── monitoring (profil opt-in : Prometheus → backend, Grafana localhost)
```

Fichiers : `infra/docker-compose.prod.yml` (prod),
`infra/docker-compose.staging.yml` (recette, ports localhost),
`infra/proxy/nginx.conf`, `infra/srs/srs.conf.template`,
`infra/.env.prod.example` (modèle sans secret).

## Environnements

| Env | Fichier | Secrets | Paiements | Accès |
|---|---|---|---|---|
| dev | backend/docker-compose.yml | dev-only | sandbox | localhost |
| staging | prod + staging.yml | recette (forts, ≠ prod) | sandbox | localhost |
| prod | docker-compose.prod.yml | serveur (env) | réels câblés | 80/443/1935 |

## Décisions (choix justifiés, pas de mode)

- **Nginx** (pas Traefik/Caddy) : config explicite versionnée, TLS
  certbot standard, WS/HLS maîtrisés ; pas de découverte auto requise.
- **SRS 5** : déjà la cible du backend (`SRS_API_URL`, HLS base) —
  réutilisé, pas remplacé. Pas de transcodage V1 (CPU) : HLS direct,
  profils ABR quand la demande le justifiera.
- **Pas de MinIO/S3 V1** : volumes persistants (`uploads`, `pgdata`) —
  object storage quand multi-serveur.
- **Pas de K8s** : Compose versionné suffit à l'échelle bêta ; chemin
  de montée en charge documenté (backend stateless × N derrière le
  proxy, Redis partagé, Postgres unique — voir scalabilité).
- **Monitoring opt-in** : healthchecks + actuator + logs JSON toujours ;
  Prometheus/Grafana via `--profile monitoring`.

## Données et flux

- Source de vérité : PostgreSQL (migrations Flyway, `ddl-auto: none`).
- Redis : rate limiting, présence, compteurs (AOF, LRU 256 Mo) —
  repli mémoire si panne, jamais de données critiques uniques.
- Médias : `/app/uploads` (volume) → HLS via SRS, VOD/clips via
  pipeline backend + FFmpeg (désactivé par défaut, `VIDEO_FFMPEG_ENABLED`).
- Backups : `pg_dump` custom quotidien, rétention 7 j, restore testé
  (voir BACKUP.md/RESTORE.md). RPO ≤ 24 h, RTO ≤ 2 h (V1, mesurés).

## Capacité (hypothèses V1, à mesurer)

Bêta (≤ 500 spectateurs simultanés, 10 lives) : 2 vCPU / 4 Go RAM /
50 Go disque / ~2 To bande passante mensuelle. Goulots attendus :
bande passante HLS (pas CPU API) puis transcodage si ABR. Coûts :
serveur + stockage + transfert dominent ; CDN quand le transfert
dépasse ~5 To/mois. Régions Afrique : 1 VPS Europe de l'Ouest
(latence ~50–90 ms) + CDN HLS ensuite ; second point de présence
si la latence mesurée dépasse 150 ms médiane.

## Limites actuelles (honnêtes)

- Mono-instance backend/SRS (pas de HA) ; objectif réaliste : 99,5 %.
- Pas d'autoscaling, pas de CDN, pas d'ABR, pas d'Alertmanager.
- `SEND /app/events/publish` (STOMP démo) sans auth — sans
  consommateur réel ; à sécuriser avec le vrai chat.
