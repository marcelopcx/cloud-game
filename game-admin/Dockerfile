# Panel de administración del catálogo. En el servidor se publica detrás del nginx
# de game-client en /admin/ (con basic auth); este nginx solo escucha en loopback.
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG BASE_PATH=/admin/
ARG VITE_CRUD_URL=/admin/api
ENV VITE_CRUD_URL=$VITE_CRUD_URL
RUN npx tsc -b && npx vite build --base="$BASE_PATH"

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx/conf.d
ENV NGINX_ENVSUBST_FILTER=ADMIN_LISTEN|CRUD_UPSTREAM
ENV ADMIN_LISTEN=127.0.0.1:8097
ENV CRUD_UPSTREAM=http://127.0.0.1:8096
EXPOSE 8097
