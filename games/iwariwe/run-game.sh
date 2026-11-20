#!/bin/sh
# Iwariwë (Godot 2.1): runtime compilado para la plataforma + .pck embebido en el ejecutable original.
set -eu

LOG="${CACHE_ROOT:-/cache}/iwariwe.log"
exec >>"$LOG" 2>&1
echo "=== Iwariwë $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

# user:// (saves y config) va a ~/.godot.
export HOME="${CACHE_ROOT:-/cache}/iwariwe-home"
mkdir -p "$HOME"

exec /opt/iwariwe/godot \
  -main_pack /opt/iwariwe/game.pck \
  -ad PulseAudio \
  -r "${STATION_SIZE:-1280x720}" \
  -p 0x0 \
  "$@"
