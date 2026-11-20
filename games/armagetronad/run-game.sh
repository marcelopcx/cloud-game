#!/bin/sh
# Armagetron Advanced: ventana del tamaño de Xvfb; xfocus-game (game-station) la ajusta y le da foco.
set -eu

LOG="${CACHE_ROOT:-/cache}/armagetronad.log"
exec >>"$LOG" 2>&1
echo "=== Armagetron Advanced $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

SIZE="${STATION_SIZE:-1280x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in '' | *[!0-9]*) W=1280 ;; esac
case "$H" in '' | *[!0-9]*) H=720 ;; esac

export HOME="${HOME:-/root}"
export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# Resolución custom: sin esto abre 640x480 y xfocus-game estira una ventana vacía.
VAR_DIR="$HOME/.armagetronad/var"
mkdir -p "$VAR_DIR"
if [ ! -f "$VAR_DIR/user.cfg" ]; then
  cat >"$VAR_DIR/user.cfg" <<CFG
ARMAGETRON_SCREENMODE -1
ARMAGETRON_WINDOWSIZE -1
ARMAGETRON_SCREENMODE_W $W
ARMAGETRON_SCREENMODE_H $H
ARMAGETRON_WINDOWSIZE_W $W
ARMAGETRON_WINDOWSIZE_H $H
CFG
fi

exec /usr/games/armagetronad -w "$@"
