# Sparks and Paths (Spaceship Games, Venezuela) · Godot 4.2.1, renderer gl_compatibility.
# El juego no se versiona: bajá el build de Windows de https://dewott2501.itch.io/sparks-and-paths
# (SnP-WINDOWS.zip) y dejalo en games/sparks-and-paths/. El .pck no depende de la plataforma: se
# corre con el binario oficial linux.arm64 de la misma versión del motor.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest
FROM ${RUNTIME_IMAGE}
ARG GODOT_VERSION=4.2.1
ARG GODOT_SHA256=3bfac95ff1e0f54a6bf2fb7712de6a31cbb5b12a0a3cc525981768fad589ee42
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
      libx11-6 libxcursor1 libxinerama1 libxrandr2 libxi6 libxext6 libxrender1 libgl1 \
      libasound2 libpulse0 libudev1 libfontconfig1 \
    && case "$(uname -m)" in \
      aarch64) ASSET=linux.arm64 ;; \
      *) echo "Sparks and Paths: esta imagen solo está verificada en aarch64" >&2; exit 1 ;; \
    esac \
    && curl -fL --retry 3 -o /tmp/godot.zip \
      "https://github.com/godotengine/godot/releases/download/${GODOT_VERSION}-stable/Godot_v${GODOT_VERSION}-stable_${ASSET}.zip" \
    && echo "${GODOT_SHA256}  /tmp/godot.zip" | sha256sum -c - \
    && mkdir -p /opt/sparks-and-paths && unzip -q /tmp/godot.zip -d /tmp/godot \
    && mv /tmp/godot/Godot_v* /opt/sparks-and-paths/godot && chmod +x /opt/sparks-and-paths/godot \
    && rm -rf /tmp/godot /tmp/godot.zip /var/lib/apt/lists/*
COPY . /tmp/snp-ctx/
RUN set -e; cd /tmp/snp-ctx; \
    for z in *.zip; do [ -f "$z" ] && unzip -qo "$z"; done; \
    PCK=$(find . -type f -iname '*.pck' | head -1); \
    if [ -z "$PCK" ]; then \
      echo "Sparks and Paths: falta SnP-WINDOWS.zip (o SparksandPaths.pck) en games/sparks-and-paths/" >&2; exit 1; \
    fi; \
    mv "$PCK" /opt/sparks-and-paths/game.pck; \
    rm -rf /tmp/snp-ctx
COPY run-game.sh /opt/sparks-and-paths/run-game.sh
RUN chmod +x /opt/sparks-and-paths/run-game.sh
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library sparks-and-paths 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=sparks-and-paths
LABEL org.cloudgame.game.version="1.0"
