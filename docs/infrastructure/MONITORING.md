# FLICK — Monitoring, logs, alertes

## Toujours actif

- Healthchecks Docker (proxy/frontend/backend/postgres/redis) + sonde
  métier `/api/health` (liveness/readiness).
- Logs JSON structurés (`timestamp, level, service, requestId, event,
  duration, status`) via logback ; `X-Request-Id` posé par le proxy et
  corrélé au MDC backend (`RequestIdFilter` existant) — jamais de secret.
- Métriques Micrometer : `/actuator/prometheus` (réseau interne seul ;
  le proxy retourne 404 sur `/actuator/**` — vérifié par smoke.sh §7).

## Opt-in (`--profile monitoring`)

Prometheus (rétention 30 j, scrape 15 s) + Grafana (localhost:3000,
compte admin par env, signup désactivé) + dashboard provisionné
**FLICK — API** (UP, req/s par statut, latences p50/p95, JVM).
Alertes mesurables : `BackendDown`, `High5xxRate`, `HighLatencyP95`,
`JwtUnauthorizedSpike` (voir `infra/monitoring/prometheus/alerts.yml` ;
Alertmanager = étape suivante).

## Sécurité observable

401/403/429 par route (force-brute, abus), opérations admin, échecs
webhook/paiement, refus ingest (401/403) — tableaux Grafana à étendre
sans logger de données sensibles.

## À venir (mesuré, pas promis)

Node exporter (disque/CPU hôte), Loki (logs), Alertmanager (paging),
SLO 99,5 % → 99,9 % avec la HA (backend × N, Postgres répliqué).
