#!/bin/sh
# Freedoom (PrBoom+): ventana del tamaño de Xvfb; xfocus-game (game-station) la ajusta y le da foco.
set -eu

LOG="${CACHE_ROOT:-/cache}/freedoom.log"
exec >>"$LOG" 2>&1
echo "=== Freedoom (PrBoom+) $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

SIZE="${STATION_SIZE:-1280x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in '' | *[!0-9]*) W=1280 ;; esac
case "$H" in '' | *[!0-9]*) H=720 ;; esac

export HOME="${HOME:-/root}"
export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

exec /usr/games/prboom-plus -iwad /usr/share/games/doom/freedoom2.wad \
  -window -width "$W" -height "$H" "$@"
