# Bloody Roar II Recompiled (novapowers0) · recompilación estática PS1 → C (psxrecomp), build nativo arm64.
# Necesita tu disco USA (SCUS-94424, Redump) en games/bloodyroar2/: "Bloody Roar II (USA).bin" + ".cue".
# El release upstream es solo x64: el C del juego se genera acá desde el disco y se compila.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS build
ARG BR2_REF=a6c88db20cdf234576fd4439dc966f79a7a7ff80
ENV DEBIAN_FRONTEND=noninteractive
# Sin glslc en jammy: el backend Vulkan se compila como stub (PSX_ENABLE_VULKAN=OFF) y se usa OpenGL.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential cmake ninja-build pkg-config python3 git ca-certificates curl \
        libgl1-mesa-dev libx11-dev libxext-dev libxrandr-dev libxcursor-dev libxi-dev libxss-dev \
        libxtst-dev libxkbcommon-dev libpulse-dev libasound2-dev libudev-dev libdbus-1-dev \
    && rm -rf /var/lib/apt/lists/*
RUN git clone https://github.com/novapowers0/BloodyRoar2Recomp.git /src \
    && git -C /src checkout "$BR2_REF" \
    && git -C /src submodule update --init --recursive
WORKDIR /src
RUN ./psxrecomp/tools/ci/build_emitters.sh

FROM build AS assets
COPY ["Bloody Roar II (USA).bin", "Bloody Roar II (USA).cue", "/src/disc/"]
# generate valida tamaño/MD5/SHA-1 del Track 01 (game_us.toml) y extrae SCUS_944.24 a disc/.
RUN python3 psxrecomp/psxrecomp_cli.py generate --config game_us.toml --project-root . \
        --disc "disc/Bloody Roar II (USA).cue" \
    && cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DPSX_ENABLE_VULKAN=OFF \
    && cmake --build build --target psx-runtime-us -j"$(nproc)" \
    && install -d /opt/bloodyroar2/mods \
    && install -m 755 build/BloodyRoar2_Recompiled_USA /opt/bloodyroar2/ \
    && cp -a build/bios build/assets build/game_us.toml build/game_options.toml /opt/bloodyroar2/ \
    && cp -a build/mods/bundled /opt/bloodyroar2/mods/ \
    # El juego lee el CD en runtime (no solo en el build): el disco va en la imagen.
    && cp -a disc /opt/bloodyroar2/disc

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
ENV STATION_SIZE=960x720
RUN apt-get update && apt-get install -y --no-install-recommends \
        libopengl0 libxcursor1 libxss1 libxkbcommon0 libudev1 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=assets /opt/bloodyroar2 /opt/bloodyroar2
COPY run-game.sh /opt/bloodyroar2/run-game.sh
RUN chmod +x /opt/bloodyroar2/run-game.sh \
    && ! ldd /opt/bloodyroar2/BloodyRoar2_Recompiled_USA | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library bloodyroar2 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=bloodyroar2
LABEL org.cloudgame.game.version="0.7.1"
