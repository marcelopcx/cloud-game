# EVERYDAYNESS;; (nouzivr, Venezuela) · novela visual en Ren'Py.
# El juego no se versiona: bajá el build "PC" (Windows/Linux, .zip) de https://nouzivr.itch.io/everydayness
# y dejalo en games/everydayness/. El zip trae el motor y los datos, pero solo con Python para x86_64:
# se agrega el lib/py*-linux-aarch64 del SDK ARM oficial de la misma versión de Ren'Py.
ARG RUNTIME_IMAGE=cloudgame/game-station-runtime:latest

FROM ubuntu:22.04 AS game
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl unzip bzip2 \
    && rm -rf /var/lib/apt/lists/*
COPY . /tmp/ctx/
RUN set -e; cd /tmp/ctx; \
    ZIP=$(find . -maxdepth 1 -type f -iname '*.zip' | head -1); \
    if [ -z "$ZIP" ]; then \
      echo "EVERYDAYNESS: falta el .zip del build PC de itch.io en games/everydayness/" >&2; exit 1; \
    fi; \
    unzip -q "$ZIP" -d /tmp/unz; \
    ROOT=$(dirname "$(find /tmp/unz -maxdepth 3 -type d -name renpy | head -1)"); \
    mv "$ROOT" /opt/everydayness; \
    VC=/opt/everydayness/renpy/vc_version.py; \
    V=$(sed -n "s/^version = '\([0-9]*\.[0-9]*\.[0-9]*\).*/\1/p" "$VC"); \
    if [ -z "$V" ]; then \
      V=$(sed -n 's/^ *version_tuple = (\([0-9]*\), *\([0-9]*\), *\([0-9]*\).*/\1.\2.\3/p' /opt/everydayness/renpy/__init__.py | head -1); \
    fi; \
    echo "EVERYDAYNESS: Ren'Py $V"; \
    curl -fL --retry 3 -o /tmp/sdk.tar.bz2 "https://www.renpy.org/dl/${V}/renpy-${V}-sdkarm.tar.bz2"; \
    mkdir /tmp/sdk && tar xjf /tmp/sdk.tar.bz2 -C /tmp/sdk --wildcards '*/lib/py*-linux-aarch64*'; \
    cp -r --no-preserve=ownership /tmp/sdk/*/lib/py*-linux-aarch64* /opt/everydayness/lib/; \
    rm -rf /tmp/ctx /tmp/unz /tmp/sdk /tmp/sdk.tar.bz2; \
    SH=$(find /opt/everydayness -maxdepth 1 -name '*.sh' | head -1); chmod +x "$SH"; \
    # El lanzador del SDK busca <nombre>.py según su propio nombre: se copia con el del juego, como
    # el de x86_64 (lib/py3-linux-x86_64/<juego>).
    for d in /opt/everydayness/lib/py*-linux-aarch64; do cp "$d/renpy" "$d/$(basename "$SH" .sh)"; done; \
    printf '#!/bin/sh\nexec "%s" "$@"\n' "$SH" > /opt/everydayness/launch.sh; chmod +x /opt/everydayness/launch.sh

FROM ${RUNTIME_IMAGE}
COPY --from=game /opt/everydayness /opt/everydayness
COPY run-game.sh /opt/everydayness/run-game.sh
RUN chmod +x /opt/everydayness/run-game.sh
COPY manifest.yaml /opt/game/manifest.yaml
RUN station-seed-library everydayness 1.0.0
LABEL org.cloudgame.role=game-station
LABEL org.cloudgame.game.id=everydayness
LABEL org.cloudgame.game.version="1.0"
