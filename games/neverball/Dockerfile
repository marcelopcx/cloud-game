# Neverball · paquete Ubuntu jammy (1.6.0); arranque vía run-game.sh (tamaño = STATION_SIZE).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest
FROM ${RUNTIME_IMAGE}

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends neverball \
    && rm -rf /var/lib/apt/lists/*

COPY run-game.sh /opt/game/run-game.sh
RUN chmod +x /opt/game/run-game.sh

COPY manifest.yaml /opt/game/manifest.yaml
# Debe coincidir con prepare del cliente: /library/<id>/1.0.0 (ver game-client station.ts).
RUN station-seed-library neverball 1.0.0

LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=neverball
LABEL org.cloudgame.game.version="1.6.0"
