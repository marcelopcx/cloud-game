# Documentación de las APIs con Scalar. En el servidor se publica detrás del nginx de
# game-client en /api-docs/ (con el basic auth del admin); este nginx solo escucha en loopback.
FROM node:22-bookworm-slim AS scalar
ARG SCALAR_VERSION=1.72.2
ARG JS_YAML_VERSION=4.1.0
WORKDIR /tmp/scalar
# Bundles empaquetados en la imagen (Scalar y js-yaml para leer los specs): sin CDN.
RUN npm pack "@scalar/api-reference@${SCALAR_VERSION}" "js-yaml@${JS_YAML_VERSION}" --silent \
    && tar xzf scalar-api-reference-*.tgz package/dist/browser/standalone.js \
    && mkdir yaml && tar xzf js-yaml-*.tgz -C yaml package/dist/js-yaml.min.js

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY index.html app.js theme.css logo.svg favicon.svg /usr/share/nginx/html/
COPY openapi /usr/share/nginx/html/openapi
COPY --from=scalar /tmp/scalar/package/dist/browser/standalone.js /usr/share/nginx/html/scalar.js
COPY --from=scalar /tmp/scalar/yaml/package/dist/js-yaml.min.js /usr/share/nginx/html/js-yaml.js
ENV NGINX_ENVSUBST_OUTPUT_DIR=/etc/nginx/conf.d
ENV NGINX_ENVSUBST_FILTER=DOCS_LISTEN|CRUD_UPSTREAM|LOADER_UPSTREAM|LOADER_API_TOKEN|DISPATCHER_UPSTREAM|DISPATCHER_API_TOKEN|STATION_HOST
ENV DOCS_LISTEN=127.0.0.1:8098
ENV CRUD_UPSTREAM=http://127.0.0.1:8096
ENV LOADER_UPSTREAM=http://127.0.0.1:8095
ENV LOADER_API_TOKEN=
ENV DISPATCHER_UPSTREAM=http://127.0.0.1:8094
ENV DISPATCHER_API_TOKEN=
ENV STATION_HOST=127.0.0.1
EXPOSE 8098
