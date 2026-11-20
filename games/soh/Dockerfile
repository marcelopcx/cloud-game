# Ship of Harkinian 9.2.3 · Zelda: Ocarina of Time (decompilación zeldaret/oot + libultraship), build nativo arm64.
# Necesita tu ROM en games/soh/baserom.z64 (formato .z64; versiones en supportedHashes.json upstream).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS toolchain
ENV DEBIAN_FRONTEND=noninteractive
COPY build-deps.sh /tmp/build-deps.sh
RUN sh /tmp/build-deps.sh

FROM toolchain AS build
ARG SOH_TAG=9.2.3
RUN git clone --depth 1 --branch "$SOH_TAG" --recurse-submodules --shallow-submodules \
      https://github.com/HarbourMasters/Shipwright.git /src
WORKDIR /src
RUN cmake -H. -Bbuild-cmake -GNinja -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build-cmake --target GenerateSohOtr \
    && cmake --build build-cmake -j"$(nproc)"

FROM build AS assets
COPY baserom.z64 /src/OTRExporter/baserom.z64
# ZAPD + OTRExporter generan oot.o2r (u oot-mq.o2r si el ROM es Master Quest).
RUN cmake --build build-cmake --target ExtractAssets \
    && install -d /opt/soh/lib \
    && cp -a build-cmake/soh/soh.elf /opt/soh/ \
    && cp -a build-cmake/soh/*.o2r /opt/soh/ \
    # SoH revisa al arrancar que exista assets/ junto al binario; sin ella muestra "Extractor assets not found" y sale.
    && cp -a build-cmake/soh/assets /opt/soh/ \
    && if [ -f build-cmake/soh/gamecontrollerdb.txt ]; then cp build-cmake/soh/gamecontrollerdb.txt /opt/soh/; fi \
    && cp -a /usr/local/lib/libSDL2*.so* /usr/local/lib/libtinyxml2.so* /usr/local/lib/libzip.so* /opt/soh/lib/ \
    && ls -la /opt/soh \
    && ls /opt/soh/oot*.o2r

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpng16-16 libglew2.2 libspdlog1 libfmt8 libogg0 libvorbis0a libvorbisfile3 \
        libopus0 libopusfile0 libusb-1.0-0 libbz2-1.0 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=assets /opt/soh /opt/soh
COPY run-game.sh /opt/soh/run-game.sh
RUN chmod +x /opt/soh/run-game.sh \
    && ! LD_LIBRARY_PATH=/opt/soh/lib ldd /opt/soh/soh.elf | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library soh 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=soh
LABEL org.cloudgame.game.version="9.2.3"
