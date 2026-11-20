# sm (snesrev) · Super Metroid — reimplementación en C, build nativo arm64.
# Necesita tu ROM en games/sm/sm.smc (SHA1 da957f0d…); se usa también en ejecución.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS build
ARG SM_REF=578f90b3cc49557bb70060ad033bb90b8cf8ac50
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential git ca-certificates libsdl2-dev \
    && rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/snesrev/sm.git /src \
    && git -C /src checkout "$SM_REF"
WORKDIR /src
RUN make -j"$(nproc)" sm \
    && install -d /opt/sm \
    && install -m 755 sm /opt/sm/sm \
    && install -m 644 sm.ini /opt/sm/

FROM ${RUNTIME_IMAGE}
# Framebuffer Xvfb = captura WebRTC (4:3). El manifiesto también declara size: 960x720.
ENV STATION_SIZE=960x720
COPY --from=build /opt/sm /opt/sm
COPY sm.smc /opt/sm/sm.smc
RUN echo "da957f0d63d14cb441d215462904c4fa8519c613  /opt/sm/sm.smc" | sha1sum -c - \
    || { echo "sm.smc: se espera Super Metroid (JU) sin header" >&2; exit 1; }
COPY run-game.sh /opt/sm/run-game.sh
RUN chmod +x /opt/sm/run-game.sh \
    && ! ldd /opt/sm/sm | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library sm 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=sm
