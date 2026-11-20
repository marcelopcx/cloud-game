# BattleShip · Super Smash Bros. 64 (GameWave) — build nativo arm64 + ROM propio.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest
FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
# Framebuffer Xvfb = captura WebRTC (4:3). El manifiesto también declara size: 960x720.
ENV STATION_SIZE=960x720
COPY baserom.us.z64 /build/baserom.us.z64
COPY install.sh /tmp/install-battleship.sh
COPY run-game.sh /tmp/battleship-run-game.sh
RUN chmod +x /tmp/install-battleship.sh \
    && /tmp/install-battleship.sh \
    && rm /tmp/install-battleship.sh
# install.sh purga herramientas de build; la estación necesita curl para el healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY BattleShip.cfg.json.in /opt/battleship/BattleShip.cfg.json.in
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library battleship 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=battleship
