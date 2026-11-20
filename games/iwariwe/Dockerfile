# Iwariwë: El dueño del fuego (Jarcelo, Venezuela) · Godot 2.1.
# El juego no se versiona: bajá Iwariwe_2019_64.tar.gz (Linux 64) de
# https://jarcelo.itch.io/iwariwe-el-dueo-del-fuego y dejalo en games/iwariwe/. El ejecutable x86_64
# trae el .pck embebido; Godot 2.1 no tiene build oficial arm64, así que se compila el runtime
# (template release, sin editor) y se le pasa el ejecutable original como -main_pack.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS godot2
ARG GODOT_VERSION=2.1.6
ARG GODOT_SRC_SHA256=5bfc57f2ee24dccfdcefe491a3be1a07b39299f2fcfa03202b8b4c6c2dd5122b
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
      gcc-9 g++-9 make scons pkg-config \
      libx11-dev libxcursor-dev libxinerama-dev libxrandr-dev libxi-dev \
      libgl1-mesa-dev libglu1-mesa-dev libasound2-dev libpulse-dev \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fL --retry 3 -o /tmp/godot.tar.gz \
      "https://github.com/godotengine/godot/archive/refs/tags/${GODOT_VERSION}-stable.tar.gz" \
    && echo "${GODOT_SRC_SHA256}  /tmp/godot.tar.gz" | sha256sum -c - \
    && tar xzf /tmp/godot.tar.gz -C /tmp && rm /tmp/godot.tar.gz
# gcc 9 (el de la época de 2.1.6) y C++14: con gcc 11/C++17 el código de 2.1 no compila limpio.
# Sin módulo openssl: el juego no usa HTTPS y la 1.0 embebida no compila en aarch64.
# Sin udev: 2.1 solo acepta pads con ID_INPUT_JOYSTICK de la base de udev, que en el contenedor no
# existe; sin udev recorre /dev/input/event* (donde la estación crea los nodos de sus pads).
RUN cd /tmp/godot-${GODOT_VERSION}-stable \
    && scons -j"$(nproc)" platform=x11 target=release tools=no \
      CC=gcc-9 CXX=g++-9 CCFLAGS="-fpermissive -Wno-error" CXXFLAGS="-std=gnu++14" \
      builtin_openssl=yes module_openssl_enabled=no udev=no \
    && mkdir -p /opt/iwariwe \
    && cp bin/godot.x11.opt.* /opt/iwariwe/godot \
    && strip /opt/iwariwe/godot

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      libx11-6 libxcursor1 libxinerama1 libxrandr2 libxi6 libgl1 libglu1-mesa \
      libasound2 libpulse0 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=godot2 /opt/iwariwe /opt/iwariwe
COPY . /tmp/iw-ctx/
RUN set -e; cd /tmp/iw-ctx; \
    for t in *.tar.gz; do [ -f "$t" ] && tar xzf "$t"; done; \
    PCK=$(find . -type f \( -name 'Iwariwe_*64' -o -iname '*.pck' \) | head -1); \
    if [ -z "$PCK" ]; then \
      echo "Iwariwë: falta Iwariwe_2019_64.tar.gz (build Linux 64 de itch.io) en games/iwariwe/" >&2; exit 1; \
    fi; \
    mv "$PCK" /opt/iwariwe/game.pck; \
    rm -rf /tmp/iw-ctx; \
    if ldd /opt/iwariwe/godot | grep 'not found'; then echo "Iwariwë: faltan bibliotecas" >&2; exit 1; fi
COPY run-game.sh /opt/iwariwe/run-game.sh
RUN chmod +x /opt/iwariwe/run-game.sh
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library iwariwe 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=iwariwe
LABEL org.cloudgame.game.version="2019"
