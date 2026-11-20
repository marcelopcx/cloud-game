#!/bin/sh
# Sparks and Paths (Godot 4.2.1): ventana del tamaño del display; xfocus-game le da foco.
set -eu

LOG="${CACHE_ROOT:-/cache}/sparks-and-paths.log"
exec >>"$LOG" 2>&1
echo "=== Sparks and Paths $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

# user:// (saves y config) va a ~/.local/share/godot.
export HOME="${CACHE_ROOT:-/cache}/sparks-and-paths-home"
mkdir -p "$HOME"

exec /opt/sparks-and-paths/godot \
  --main-pack /opt/sparks-and-paths/game.pck \
  --rendering-driver opengl3 \
  --audio-driver PulseAudio \
  --windowed \
  --resolution "${STATION_SIZE:-1280x720}" \
  --position 0,0 \
  "$@"
