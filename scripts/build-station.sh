#!/usr/bin/env bash
# Construye el runtime y la imagen de un juego.
# Uso: ./scripts/build-station.sh <id-juego>
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
GAME=${1:-${GAME:-supertuxkart}}
if [[ ! -f "$ROOT/games/${GAME}/Dockerfile" ]]; then
  echo "juego desconocido: $GAME (no existe games/${GAME}/Dockerfile)" >&2
  exit 1
fi
docker build -t cloudgame/game-station-runtime:latest \
  -f "$ROOT/game-station/Dockerfile" \
  "$ROOT/game-station"
docker build \
  -t "cloudgame/game-station-${GAME}" \
  --build-arg RUNTIME_IMAGE=cloudgame/game-station-runtime:latest \
  "$ROOT/games/${GAME}"
echo "listo cloudgame/game-station-${GAME}"
