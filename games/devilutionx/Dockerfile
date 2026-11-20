# DevilutionX 1.5.5 · Diablo (devilution: decompilación de Diablo 1) — binario upstream linux-aarch64
# + spawn.mpq (shareware, redistribuible). Con DIABDAT.MPQ propio se juega la versión completa.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest
FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
COPY install.sh /tmp/install-devilutionx.sh
RUN sh /tmp/install-devilutionx.sh && rm /tmp/install-devilutionx.sh
# Opcional: DIABDAT.MPQ (CD / GOG) → juego completo en lugar del shareware.
COPY . /tmp/devx-ctx/
RUN if [ -f /tmp/devx-ctx/DIABDAT.MPQ ]; then mv /tmp/devx-ctx/DIABDAT.MPQ /opt/devilutionx/diabdat.mpq; fi \
    && if [ -f /tmp/devx-ctx/hellfire.mpq ]; then mv /tmp/devx-ctx/hellfire.mpq /opt/devilutionx/; fi \
    && rm -rf /tmp/devx-ctx
COPY run-game.sh /opt/devilutionx/run-game.sh
RUN chmod +x /opt/devilutionx/run-game.sh
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library devilutionx 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=devilutionx
LABEL org.cloudgame.game.version="1.5.5"
