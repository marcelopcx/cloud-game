# Dusklight 2.0.2 · The Legend of Zelda: Twilight Princess (port nativo de la decompilación zeldaret/tp)
# Necesita tu disco en games/dusklight/: un solo archivo .iso/.rvz/.gcm/.ciso/.gcz/.wia/.wbfs/.nfs/.tgc
# (GameCube USA o PAL; también discos Wii salvo el coreano). Requiere GPU: el render es Vulkan 1.1+
# (LOADER_GPU=true o compose.gpu.yaml). El build de Linux no trae backend OpenGL.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

# El AppImage oficial está compilado en Ubuntu 24.04 (glibc 2.39, GLIBCXX_3.4.32) y la imagen base es
# jammy (2.35). Se trae el glibc/libstdc++ de noble a una carpeta propia y se parchea el binario para
# usar ese ld.so (sin LD_LIBRARY_PATH: no se filtra a procesos hijos de jammy).
FROM ubuntu:24.04 AS noble
ARG DUSK_VERSION=2.0.2
ARG DUSK_SHA256=af9e818658ef1bb73390a4503bd5bb16224cb358c0d910fcbafe72529d5854ee
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl patchelf \
    && rm -rf /var/lib/apt/lists/*
RUN case "$(uname -m)" in \
      aarch64) ASSET=linux-arm64 ;; \
      *) echo "Dusklight: esta imagen solo está verificada en aarch64" >&2; exit 1 ;; \
    esac \
    && curl -fL --retry 3 -o /tmp/dusk.AppImage \
      "https://github.com/TwilitRealm/dusklight/releases/download/v${DUSK_VERSION}/Dusklight-v${DUSK_VERSION}-${ASSET}.AppImage" \
    && echo "${DUSK_SHA256}  /tmp/dusk.AppImage" | sha256sum -c - \
    && chmod +x /tmp/dusk.AppImage \
    && cd /tmp && ./dusk.AppImage --appimage-extract >/dev/null \
    && mkdir -p /opt/dusklight/noble \
    && cp -a /tmp/squashfs-root/usr/bin /tmp/squashfs-root/usr/lib /opt/dusklight/ \
    && rm -rf /opt/dusklight/bin/lib /opt/dusklight/bin/include \
    && cd /usr/lib/aarch64-linux-gnu \
    && cp -L ld-linux-aarch64.so.1 libc.so.6 libm.so.6 libpthread.so.0 libdl.so.2 librt.so.1 \
      libresolv.so.2 libstdc++.so.6 libgcc_s.so.1 /opt/dusklight/noble/ \
    && for exe in /opt/dusklight/bin/dusklight /opt/dusklight/bin/crashpad_handler; do \
         patchelf --set-interpreter /opt/dusklight/noble/ld-linux-aarch64.so.1 \
           --force-rpath --set-rpath '/opt/dusklight/noble:/opt/dusklight/lib' "$exe"; \
       done \
    && rm -rf /tmp/dusk.AppImage /tmp/squashfs-root

FROM ${RUNTIME_IMAGE}
ENV DEBIAN_FRONTEND=noninteractive
COPY --from=noble /opt/dusklight /opt/dusklight
# El disco: se acepta cualquier nombre, con una de las extensiones que soporta Dusklight.
COPY . /tmp/dusk-ctx/
RUN set -e; cd /tmp/dusk-ctx; \
    DISC=$(find . -maxdepth 1 -type f \( -iname '*.iso' -o -iname '*.rvz' -o -iname '*.gcm' -o -iname '*.ciso' \
      -o -iname '*.gcz' -o -iname '*.wia' -o -iname '*.wbfs' -o -iname '*.nfs' -o -iname '*.tgc' \) | head -1); \
    if [ -z "$DISC" ]; then \
      echo "Dusklight: falta el disco de Twilight Princess en games/dusklight/ (.iso, .rvz, ...)" >&2; exit 1; \
    fi; \
    mkdir -p /opt/dusklight/disc; \
    mv "$DISC" "/opt/dusklight/disc/tp.${DISC##*.}"; \
    rm -rf /tmp/dusk-ctx
COPY run-game.sh /opt/dusklight/run-game.sh
RUN chmod +x /opt/dusklight/run-game.sh
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library dusklight 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=dusklight
LABEL org.cloudgame.game.version="2.0.2"
