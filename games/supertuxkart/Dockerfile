# SuperTuxKart 1.5 · binario upstream; menú normal vía run-game.sh (pantalla + foco X11).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest
FROM ${RUNTIME_IMAGE}

ARG TARGETARCH
ARG STK_VERSION=1.5

ENV DEBIAN_FRONTEND=noninteractive
ENV STATION_SIZE=1280x720

COPY install.sh /tmp/install-stk.sh
RUN chmod +x /tmp/install-stk.sh \
    && STK_VERSION="${STK_VERSION}" TARGETARCH="${TARGETARCH}" /tmp/install-stk.sh \
    && rm /tmp/install-stk.sh

COPY run-game.sh /opt/stk/run-game.sh
RUN chmod +x /opt/stk/run-game.sh

COPY home/ /root/.config/supertuxkart/
COPY manifest.yaml /opt/game/manifest.yaml
# Debe coincidir con prepare del cliente: /library/<id>/1.0.0 (ver game-client station.ts).
RUN station-seed-library supertuxkart 1.0.0

LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=supertuxkart
LABEL org.cloudgame.game.version="${STK_VERSION}"
