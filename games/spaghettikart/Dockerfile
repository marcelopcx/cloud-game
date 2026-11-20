# SpaghettiKart 1.0.0 · Mario Kart 64 (decompilación n64decomp/mk64 + libultraship), build nativo arm64.
# Necesita tu ROM en games/spaghettikart/baserom.z64 (Mario Kart 64 US, formato .z64).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS toolchain
ENV DEBIAN_FRONTEND=noninteractive
COPY build-deps.sh /tmp/build-deps.sh
RUN sh /tmp/build-deps.sh

FROM toolchain AS build
ARG SPAGHETTI_TAG=1.0.0
RUN git clone --depth 1 --branch "$SPAGHETTI_TAG" --recurse-submodules --shallow-submodules \
      https://github.com/HarbourMasters/SpaghettiKart.git /src
WORKDIR /src
RUN cmake -H. -Bbuild-cmake -GNinja -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build-cmake --target GenerateO2R \
    && cmake --build build-cmake -j"$(nproc)"

FROM build AS assets
COPY baserom.z64 /src/baserom.us.z64
RUN cmake --build build-cmake --target ExtractAssets \
    && install -d /opt/spaghettikart/lib \
    && cp -a build-cmake/Spaghettify /opt/spaghettikart/ \
    && cp -a build-cmake/*.o2r /opt/spaghettikart/ \
    && for extra in config.yml yamls meta assets; do if [ -e build-cmake/$extra ]; then cp -a build-cmake/$extra /opt/spaghettikart/; fi; done \
    && if [ -f build-cmake/gamecontrollerdb.txt ]; then cp build-cmake/gamecontrollerdb.txt /opt/spaghettikart/; fi \
    && cp -a /usr/local/lib/libSDL2*.so* /usr/local/lib/libtinyxml2.so* /usr/local/lib/libzip.so* /opt/spaghettikart/lib/ \
    && ls -la /opt/spaghettikart \
    && test -s /opt/spaghettikart/mk64.o2r

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpng16-16 libglew2.2 libspdlog1 libfmt8 libogg0 libvorbis0a libvorbisfile3 \
        libopus0 libopusfile0 libusb-1.0-0 libbz2-1.0 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=assets /opt/spaghettikart /opt/spaghettikart
COPY run-game.sh /opt/spaghettikart/run-game.sh
RUN chmod +x /opt/spaghettikart/run-game.sh \
    && ! LD_LIBRARY_PATH=/opt/spaghettikart/lib ldd /opt/spaghettikart/Spaghettify | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library spaghettikart 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=spaghettikart
LABEL org.cloudgame.game.version="1.0.0"
