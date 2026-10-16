# ============================================================
# FLICK — frontend web (React + Vite, build statique)
# Image multi-étapes :
#   1. build → npm ci + vite build (Node LTS)
#   2. run   → nginx non-root servant dist/ (jamais le serveur Vite dev)
#
# AUCUN secret ici : seules les variables VITE_* NON sensibles sont
# injectées au BUILD (VITE_API_URL publique, VITE_GOOGLE_CLIENT_ID public).
# Images versionnées au déploiement (jamais "latest" seul en prod).
#
# Construction (depuis frontend-web/) :
#   docker build -t flick-frontend:<version> \
#     --build-arg VITE_API_URL=https://api.flick.example .
# ============================================================

# ---------- Étape 1 : construction ----------
FROM node:22-alpine AS build
WORKDIR /build

# Dépendances d'abord (cache Docker tant que package-lock ne change pas).
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# Puis sources : type-check + build production.
COPY . ./
ARG VITE_API_URL=https://api.flick.example
ARG VITE_GOOGLE_CLIENT_ID=
ARG VITE_SITE_URL=https://app.flick.example
ENV VITE_API_URL=${VITE_API_URL} \
    VITE_GOOGLE_CLIENT_ID=${VITE_GOOGLE_CLIENT_ID} \
    VITE_SITE_URL=${VITE_SITE_URL}
RUN npm run build

# ---------- Étape 2 : exécution ----------
FROM nginxinc/nginx-unprivileged:1.27-alpine

LABEL org.opencontainers.image.title="flick-frontend" \
      org.opencontainers.image.description="Frontend statique FLICK (Vite build)"

# Configuration : fallback SPA + cache long des assets fingerprintés.
COPY infra-nginx-frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

# nginx-unprivileged écoute en non-root (uid nginx).
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/ || exit 1
