# zelda3 (snesrev) · The Legend of Zelda: A Link to the Past — reimplementación en C, build nativo arm64.
# Necesita tu ROM USA en games/zelda3/zelda3.sfc (solo en el build: se extraen los assets y se descarta).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS build
ARG ZELDA3_REF=fbbb3f967a51fafe642e6140d0753979e73b4090
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential git ca-certificates libsdl2-dev python3 python3-pil python3-yaml \
    && rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/snesrev/zelda3.git /src \
    && git -C /src checkout "$ZELDA3_REF"
WORKDIR /src
RUN make -j"$(nproc)" zelda3

FROM build AS assets
COPY zelda3.sfc /src/zelda3.sfc
# restool valida el SHA256 (USA 66871d66…); falla si el ROM no es el correcto.
RUN python3 assets/restool.py --extract-from-rom \
    && test -s zelda3_assets.dat \
    && install -d /opt/zelda3 \
    && install -m 755 zelda3 /opt/zelda3/zelda3 \
    && install -m 644 zelda3_assets.dat zelda3.ini /opt/zelda3/

FROM ${RUNTIME_IMAGE}
COPY --from=assets /opt/zelda3 /opt/zelda3
COPY run-game.sh /opt/zelda3/run-game.sh
RUN chmod +x /opt/zelda3/run-game.sh \
    && ! ldd /opt/zelda3/zelda3 | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library zelda3 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=zelda3
