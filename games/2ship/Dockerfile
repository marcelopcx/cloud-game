# 2 Ship 2 Harkinian 5.0.1 · Zelda: Majora's Mask (decompilación zeldaret/mm + libultraship), build nativo arm64.
# Necesita tu ROM en games/2ship/baserom.z64 (Majora's Mask US o JP GC, formato .z64).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS toolchain
ENV DEBIAN_FRONTEND=noninteractive
COPY build-deps.sh /tmp/build-deps.sh
RUN sh /tmp/build-deps.sh

FROM toolchain AS build
ARG TWOSHIP_TAG=5.0.1
RUN git clone --depth 1 --branch "$TWOSHIP_TAG" --recurse-submodules --shallow-submodules \
      https://github.com/HarbourMasters/2ship2harkinian.git /src
WORKDIR /src
RUN cmake -H. -Bbuild-cmake -GNinja -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build-cmake --target Generate2ShipOtr \
    && cmake --build build-cmake -j"$(nproc)"

FROM build AS assets
COPY baserom.z64 /src/OTRExporter/baserom.z64
RUN cmake --build build-cmake --target ExtractAssets \
    && install -d /opt/2ship/lib \
    && cp -a build-cmake/mm/2s2h.elf /opt/2ship/ \
    && cp -a build-cmake/mm/*.o2r /opt/2ship/ \
    # 2Ship revisa al arrancar que exista assets/ junto al binario; sin ella muestra "Extractor assets not found" y sale.
    && cp -a build-cmake/mm/assets /opt/2ship/ \
    && if [ -f build-cmake/mm/gamecontrollerdb.txt ]; then cp build-cmake/mm/gamecontrollerdb.txt /opt/2ship/; fi \
    && cp -a /usr/local/lib/libSDL2*.so* /usr/local/lib/libtinyxml2.so* /usr/local/lib/libzip.so* /opt/2ship/lib/ \
    && ls -la /opt/2ship \
    && test -s /opt/2ship/mm.o2r

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpng16-16 libglew2.2 libspdlog1 libfmt8 libogg0 libvorbis0a libvorbisfile3 \
        libopus0 libopusfile0 libusb-1.0-0 libbz2-1.0 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=assets /opt/2ship /opt/2ship
COPY run-game.sh /opt/2ship/run-game.sh
RUN chmod +x /opt/2ship/run-game.sh \
    && ! LD_LIBRARY_PATH=/opt/2ship/lib ldd /opt/2ship/2s2h.elf | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library 2ship 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=2ship
LABEL org.cloudgame.game.version="5.0.1"
