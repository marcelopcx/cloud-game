# SPA S1. El browser habla REST/WS contra `/station` (mismo origen);
# nginx lo reenvía a la estación. RTP de WebRTC no pasa por acá.
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_STATION_URL=/station
# Modo multi: "/stations" → cada juego habla con /stations/<gameId>.
ARG VITE_STATIONS_URL=
# Modo loader: "/dispatcher" → el Game Dispatcher asigna la estación (nginx llega a ella por /ports/<puerto>).
ARG VITE_DISPATCHER_URL=
ARG VITE_ICE_SERVERS=stun:stun.l.google.com:19302
ARG VITE_GAME_ID=supertuxkart
# "true" muestra los juegos de prueba (estado TEST de game-crud); vacío = ocultos en el build.
ARG VITE_SHOW_TEST_GAMES=
ENV VITE_STATION_URL=$VITE_STATION_URL
ENV VITE_STATIONS_URL=$VITE_STATIONS_URL
ENV VITE_DISPATCHER_URL=$VITE_DISPATCHER_URL
ENV VITE_ICE_SERVERS=$VITE_ICE_SERVERS
ENV VITE_GAME_ID=$VITE_GAME_ID
ENV VITE_SHOW_TEST_GAMES=$VITE_SHOW_TEST_GAMES
RUN npm run build

FROM goacme/lego:v5.5.2 AS lego

FROM nginx:1.27-alpine
RUN apk add --no-cache openssl
# Cliente ACME para TLS_CERT=acme (ver docker/16-tls-certs.sh).
COPY --from=lego /lego /usr/local/bin/lego
COPY nginx.conf /etc/nginx/templates/default.conf.template
# Mapa gameId → puerto; deploy-all.sh monta encima el generado.
COPY docker/stations.conf /etc/nginx/conf.d/stations.conf
COPY docker/16-tls-certs.sh /docker-entrypoint.d/16-tls-certs.sh
RUN chmod +x /docker-entrypoint.d/16-tls-certs.sh
COPY --from=build /app/dist /usr/share/nginx/html
ENV NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx/conf.d
ENV NGINX_ENVSUBST_FILTER=STATION_UPSTREAM|DISPATCHER_UPSTREAM|ADMIN_UPSTREAM|DOCS_UPSTREAM|DOCS_SITE_UPSTREAM|CRUD_UPSTREAM
ENV STATION_UPSTREAM=http://127.0.0.1:8090
ENV DISPATCHER_UPSTREAM=http://127.0.0.1:8094
ENV ADMIN_UPSTREAM=http://127.0.0.1:8097
ENV DOCS_UPSTREAM=http://127.0.0.1:8098
ENV DOCS_SITE_UPSTREAM=http://127.0.0.1:8099
ENV CRUD_UPSTREAM=http://127.0.0.1:8096
ENV PUBLIC_HOST=localhost
ENV TLS_CERT=self
EXPOSE 80 5173 443
