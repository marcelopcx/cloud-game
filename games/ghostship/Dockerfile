# Ghostship 3.0.0 · Super Mario 64 (decompilación n64decomp/sm64 + libultraship), build nativo arm64.
# Necesita tu ROM en games/ghostship/baserom.z64 (Super Mario 64 US o JP, formato .z64).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS toolchain
ENV DEBIAN_FRONTEND=noninteractive
COPY build-deps.sh /tmp/build-deps.sh
RUN sh /tmp/build-deps.sh

FROM toolchain AS build
# ixwebsocket (multijugador de Ghostship) enlaza mbedTLS. lld: GNU ld aborta con ".eh_frame_hdr refers to overlapping FDEs".
RUN apt-get update && apt-get install -y --no-install-recommends libmbedtls-dev lld && rm -rf /var/lib/apt/lists/*
ARG GHOSTSHIP_TAG=3.0.0
RUN git clone --depth 1 --branch "$GHOSTSHIP_TAG" --recurse-submodules --shallow-submodules \
      https://github.com/HarbourMasters/Ghostship.git /src
WORKDIR /src
RUN cmake -H. -Bbuild-cmake -GNinja -DCMAKE_BUILD_TYPE=Release -DCMAKE_EXE_LINKER_FLAGS=-fuse-ld=lld \
    && cmake --build build-cmake --target GeneratePortO2R \
    && cmake --build build-cmake -j"$(nproc)"

FROM build AS assets
COPY baserom.z64 /src/baserom.us.z64
RUN cmake --build build-cmake --target ExtractAssets \
    && install -d /opt/ghostship/lib \
    && cp -a build-cmake/Ghostship /opt/ghostship/ \
    && cp -a build-cmake/*.o2r /opt/ghostship/ \
    && for extra in config.yml yamls meta assets; do if [ -e build-cmake/$extra ]; then cp -a build-cmake/$extra /opt/ghostship/; fi; done \
    && if [ -d assets ]; then cp -a assets /opt/ghostship/; fi \
    && if [ -f build-cmake/gamecontrollerdb.txt ]; then cp build-cmake/gamecontrollerdb.txt /opt/ghostship/; fi \
    && cp -a /usr/local/lib/libSDL2*.so* /usr/local/lib/libtinyxml2.so* /usr/local/lib/libzip.so* /opt/ghostship/lib/ \
    && cp -a build-cmake/libultraship/libtcc.so /opt/ghostship/lib/ \
    && ls -la /opt/ghostship \
    && test -s /opt/ghostship/sm64.o2r

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpng16-16 libglew2.2 libspdlog1 libfmt8 libogg0 libvorbis0a libvorbisfile3 \
        libopus0 libopusfile0 libusb-1.0-0 libbz2-1.0 \
        libmbedtls14 libmbedx509-1 libmbedcrypto7 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=assets /opt/ghostship /opt/ghostship
COPY run-game.sh /opt/ghostship/run-game.sh
RUN chmod +x /opt/ghostship/run-game.sh \
    && ! LD_LIBRARY_PATH=/opt/ghostship/lib ldd /opt/ghostship/Ghostship | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library ghostship 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=ghostship
LABEL org.cloudgame.game.version="3.0.0"
