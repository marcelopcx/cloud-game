#!/bin/sh
# Arranque normal: menú de STK, tamaño alineado con Xvfb y foco para input.
set -eu

LOG="${CACHE_ROOT:-/cache}/supertuxkart.log"
exec >>"$LOG" 2>&1
echo "=== SuperTuxKart $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

SIZE="${STATION_SIZE:-1280x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in '' | *[!0-9]*) W=1280 ;; esac
case "$H" in '' | *[!0-9]*) H=720 ;; esac

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0
export HOME="${HOME:-/root}"

if [ ! -x /opt/stk/run_game.sh ]; then
  echo "error: falta /opt/stk/run_game.sh — rebuild la imagen" >&2
  exit 1
fi

# Foco y ajuste de ventana: lo corre game-station (xfocus-game en bucle).
STK_ARGS="--screensize=${W}x${H}"
if [ "${STK_WINDOWED:-0}" != 1 ]; then
  STK_ARGS="$STK_ARGS --fullscreen"
fi

# shellcheck disable=SC2086
exec /opt/stk/run_game.sh $STK_ARGS "$@"
