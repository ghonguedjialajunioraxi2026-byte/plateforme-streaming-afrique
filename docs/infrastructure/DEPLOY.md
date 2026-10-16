# FLICK — Déploiement production

## Pré-requis serveur (Ubuntu LTS)

```bash
# Docker + compose plugin, firewall : 80/443/1935 seuls publics.
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp \
  && sudo ufw allow 443/tcp && sudo ufw allow 1935/tcp \
  && sudo ufw enable
```

## 1. Secrets (jamais dans Git)

```bash
mkdir -p /srv/flick && cp infra/.env.prod.example /srv/flick/.env
# Renseigner : DB_PASSWORD / JWT_SECRET (openssl rand -base64 48) /
# REDIS_PASSWORD / CORS_ALLOWED_ORIGINS / INGEST_CALLBACK_SECRET /
# PAYMENT_WEBHOOK_SECRET / versions d'images (BACKEND_VERSION=x.y.z).
chmod 600 /srv/flick/.env
```

## 2. Base initiale (ddl-auto: none — Flyway migre au boot)

Créer le schéma vide AVANT le premier démarrage (compte
moindre-privilège recommandé : `GRANT ALL ON DATABASE flick` +
`ALTER DEFAULT PRIVILEGES` sur le schéma public, pas superuser).

## 3. SRS (secret injecté, jamais commité)

```bash
set -a; . /srv/flick/.env; set +a
INGEST_CALLBACK_SECRET="$INGEST_CALLBACK_SECRET" \
  envsubst < infra/srs/srs.conf.template > infra/srs/srs.conf
```

## 4. TLS (certbot, renouvellement auto)

```bash
sudo certbot certonly --webroot -w /srv/flick/certbot \
  -d app.flick.example
cp /etc/letsencrypt/live/app.flick.example/{fullchain.pem,privkey.pem} \
  infra/proxy/certs/   # ou montage direct en lecture seule
# Renouvellement : cron certbot + recharge nginx sans coupure :
#   docker compose ... kill -s HUP proxy
```

## 5. Lancement

```bash
docker compose --env-file /srv/flick/.env \
  -f infra/docker-compose.prod.yml up -d --build
docker compose --env-file /srv/flick/.env \
  -f infra/docker-compose.prod.yml ps
BASE_URL=https://app.flick.example ./infra/scripts/smoke.sh
```

## 6. Vérifications (avant/après : §72)

Santé (`/api/health`), smoke.sh (7 contrôles dont 401 ingest/notif
et 404 actuator), logs (`docker compose logs -f backend`),
métriques (profil monitoring). Staging d'abord, prod après validation.
