#!/bin/sh
# OpenTTD: ventana del tamaño de Xvfb; xfocus-game (game-station) la ajusta y le da foco.
set -eu

LOG="${CACHE_ROOT:-/cache}/openttd.log"
exec >>"$LOG" 2>&1
echo "=== OpenTTD $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

SIZE="${STATION_SIZE:-1280x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in '' | *[!0-9]*) W=1280 ;; esac
case "$H" in '' | *[!0-9]*) H=720 ;; esac

export HOME="${HOME:-/root}"
export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# Xvfb no tiene GPU: sin video_hw_accel=false muestra un aviso al arrancar.
CFG_DIR="$HOME/.config/openttd"
mkdir -p "$CFG_DIR"
if [ ! -f "$CFG_DIR/openttd.cfg" ]; then
  printf "[misc]\nvideo_hw_accel = false\nfullscreen = false\nresolution = %s,%s\n" "$W" "$H" >"$CFG_DIR/openttd.cfg"
fi

exec /usr/games/openttd -r "${W}x${H}" "$@"
