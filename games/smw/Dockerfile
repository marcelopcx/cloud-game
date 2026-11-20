# smw (snesrev) · Super Mario World — reimplementación en C, build nativo arm64.
# Necesita tu ROM USA sin header en games/smw/smw.sfc (solo en el build: se extraen los assets).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS build
ARG SMW_REF=eae20c65c58930c8b62c76188d259579ad4130f1
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential git ca-certificates libsdl2-dev python3 python3-pil python3-yaml \
    && rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/snesrev/smw.git /src \
    && git -C /src checkout "$SMW_REF"
WORKDIR /src
RUN make -j"$(nproc)" smw

FROM build AS assets
COPY smw.sfc /src/smw.sfc
# restool valida el hash del ROM y deja smw_assets.dat (incluye el ROM para verificación).
RUN python3 assets/restool.py \
    && test -s smw_assets.dat \
    && install -d /opt/smw \
    && install -m 755 smw /opt/smw/smw \
    && install -m 644 smw_assets.dat smw.ini /opt/smw/

FROM ${RUNTIME_IMAGE}
# Framebuffer Xvfb = captura WebRTC (4:3). El manifiesto también declara size: 960x720.
ENV STATION_SIZE=960x720
COPY --from=assets /opt/smw /opt/smw
COPY run-game.sh /opt/smw/run-game.sh
RUN chmod +x /opt/smw/run-game.sh \
    && ! ldd /opt/smw/smw | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library smw 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=smw
