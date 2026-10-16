# FLICK — Rollback applicatif

Les images sont versionnées (`BACKEND_VERSION`/`FRONTEND_VERSION`, jamais
`latest` seul) : un rollback = redéployer la version précédente.

```bash
# 1. Identifier (tags déployés : voir .env + `docker compose images`).
# 2. Repointer les versions précédentes dans /srv/flick/.env.
# 3. Redéployer SANS reconstruire (images taguées conservées) :
docker compose --env-file /srv/flick/.env \
  -f infra/docker-compose.prod.yml up -d backend frontend proxy
# 4. Vérifier : smoke.sh + logs + métriques (erreurs, latence).
# 5. Base : le rollback applicatif NE remonte PAS les migrations Flyway
#    (irréversibles par design) — toute migration destructive exige une
#    migration corrective vers l'avant, testée en staging d'abord.
# 6. Streaming/paiements : vérifier un live (hook ingest 200), un webhook
#    sandbox en staging ; en prod, webhooks HTTPS + idempotence protègent
#    des doubles crédits pendant l'opération.
```

Zéro-downtime : redéploiement service par service (proxy en dernier) ;
sessions JWT stateless (pas de sticky requis) ; WebSocket reconnecte
(côté clients, backoff déjà prévu).
