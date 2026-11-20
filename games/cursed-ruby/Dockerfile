# Cursed Ruby (MiracleJester, Venezuela) · Godot 3.4.3.
# El juego no se versiona: bajá CursedRuby.exe de https://miraclejester.itch.io/cursed-ruby y dejalo en
# games/cursed-ruby/. El .exe trae el .pck embebido, que Godot abre directo con --main-pack. Godot 3.4
# no tiene build oficial arm64: se usa el binario oficial linux.arm64 de 3.6 (misma rama 3.x, carga
# packs de 3.4).
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest
FROM ${RUNTIME_IMAGE}
ARG GODOT_VERSION=3.6
ARG GODOT_SHA256=3c57cc70618bf473e335eeaac0a0bbe034d72793956358829e4f25b40fd7d1a1
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
      libx11-6 libxcursor1 libxinerama1 libxrandr2 libxi6 libxext6 libxrender1 libgl1 \
      libasound2 libpulse0 libudev1 \
    && case "$(uname -m)" in \
      aarch64) ASSET=linux.arm64 ;; \
      *) echo "Cursed Ruby: esta imagen solo está verificada en aarch64" >&2; exit 1 ;; \
    esac \
    && curl -fL --retry 3 -o /tmp/godot.zip \
      "https://github.com/godotengine/godot/releases/download/${GODOT_VERSION}-stable/Godot_v${GODOT_VERSION}-stable_${ASSET}.zip" \
    && echo "${GODOT_SHA256}  /tmp/godot.zip" | sha256sum -c - \
    && mkdir -p /opt/cursed-ruby && unzip -q /tmp/godot.zip -d /tmp/godot \
    && mv /tmp/godot/Godot_v* /opt/cursed-ruby/godot && chmod +x /opt/cursed-ruby/godot \
    && rm -rf /tmp/godot /tmp/godot.zip /var/lib/apt/lists/*
COPY . /tmp/cr-ctx/
RUN set -e; cd /tmp/cr-ctx; \
    for z in *.zip; do [ -f "$z" ] && unzip -qo "$z"; done; \
    PCK=$(find . -type f \( -iname '*.pck' -o -iname '*.exe' \) | head -1); \
    if [ -z "$PCK" ]; then \
      echo "Cursed Ruby: falta CursedRuby.exe (build Windows de itch.io) en games/cursed-ruby/" >&2; exit 1; \
    fi; \
    mv "$PCK" /opt/cursed-ruby/game.pck; \
    rm -rf /tmp/cr-ctx
COPY run-game.sh /opt/cursed-ruby/run-game.sh
RUN chmod +x /opt/cursed-ruby/run-game.sh
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library cursed-ruby 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=cursed-ruby
LABEL org.cloudgame.game.version="1.0"
