#!/bin/sh
# EVERYDAYNESS;; (Ren'Py): ventana redimensionable, xfocus-game la estira al display.
set -eu

LOG="${CACHE_ROOT:-/cache}/everydayness.log"
exec >>"$LOG" 2>&1
echo "=== EVERYDAYNESS $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

# Saves y preferencias (~/.renpy/<save_directory>).
export HOME="${CACHE_ROOT:-/cache}/everydayness-home"
mkdir -p "$HOME"

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

exec /opt/everydayness/launch.sh "$@"
