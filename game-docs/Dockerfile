# Documentación del proyecto (Astro Starlight). Se construye desde la raíz del repo porque el
# contenido sale de docs/ y de los README de cada módulo (scripts/sync-docs.mjs):
#   docker build -f game-docs/Dockerfile .
# En el servidor se publica detrás del nginx de game-client en /docs/ (basic auth del admin).
FROM node:22-bookworm-slim AS build
ENV ASTRO_TELEMETRY_DISABLED=1
WORKDIR /repo/game-docs
COPY game-docs/package.json game-docs/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY README.md /repo/
COPY docs /repo/docs
COPY games/README.md /repo/games/README.md
COPY game-api/README.md /repo/game-api/README.md
COPY game-docs ./
RUN npm run build

FROM nginx:1.27-alpine
COPY game-docs/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /repo/game-docs/dist /usr/share/nginx/html
ENV NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx/conf.d
ENV NGINX_ENVSUBST_FILTER=DOCS_SITE_LISTEN
ENV DOCS_SITE_LISTEN=127.0.0.1:8099
EXPOSE 8099
