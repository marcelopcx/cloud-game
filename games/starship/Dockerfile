# Starship 2.0.0 · Star Fox 64 (decompilación sonicdcer/sf64 + libultraship), build nativo arm64.
# Necesita tu ROM en games/starship/baserom.z64 (Star Fox 64 US 1.0 o 1.1, formato .z64).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS toolchain
ENV DEBIAN_FRONTEND=noninteractive
COPY build-deps.sh /tmp/build-deps.sh
RUN sh /tmp/build-deps.sh

FROM toolchain AS build
ARG STARSHIP_TAG=v2.0.0
RUN git clone --depth 1 --branch "$STARSHIP_TAG" --recurse-submodules --shallow-submodules \
      https://github.com/HarbourMasters/Starship.git /src
WORKDIR /src
# -mfpu=neon es de ARM 32 bits: GCC aarch64 lo rechaza y NEON ya viene activado.
RUN sed -i "s/set(CPU_OPTION -mfpu=neon)/set(CPU_OPTION \"\")/" CMakeLists.txt \
    && grep -q "set(CPU_OPTION \"\")" CMakeLists.txt
RUN cmake -H. -Bbuild-cmake -GNinja -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build-cmake --target GeneratePortO2R \
    && cmake --build build-cmake -j"$(nproc)"

FROM build AS assets
COPY baserom.z64 /src/baserom.z64
RUN cmake --build build-cmake --target ExtractAssets \
    && install -d /opt/starship/lib \
    && cp -a build-cmake/Starship /opt/starship/ \
    && cp -a build-cmake/*.o2r /opt/starship/ \
    && for extra in config.yml yamls meta assets; do if [ -e build-cmake/$extra ]; then cp -a build-cmake/$extra /opt/starship/; fi; done \
    && if [ -f build-cmake/gamecontrollerdb.txt ]; then cp build-cmake/gamecontrollerdb.txt /opt/starship/; fi \
    && cp -a /usr/local/lib/libSDL2*.so* /usr/local/lib/libtinyxml2.so* /usr/local/lib/libzip.so* /opt/starship/lib/ \
    && ls -la /opt/starship \
    && test -s /opt/starship/sf64.o2r

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpng16-16 libglew2.2 libspdlog1 libfmt8 libogg0 libvorbis0a libvorbisfile3 \
        libopus0 libopusfile0 libusb-1.0-0 libbz2-1.0 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=assets /opt/starship /opt/starship
COPY run-game.sh /opt/starship/run-game.sh
RUN chmod +x /opt/starship/run-game.sh \
    && ! LD_LIBRARY_PATH=/opt/starship/lib ldd /opt/starship/Starship | grep 'not found'
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library starship 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=starship
LABEL org.cloudgame.game.version="2.0.0"
