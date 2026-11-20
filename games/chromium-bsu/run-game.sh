#!/bin/sh
# Chromium B.S.U.: ventana del tamaño de Xvfb; xfocus-game (game-station) la ajusta y le da foco.
set -eu

LOG="${CACHE_ROOT:-/cache}/chromium-bsu.log"
exec >>"$LOG" 2>&1
echo "=== Chromium B.S.U. $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

SIZE="${STATION_SIZE:-1280x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in '' | *[!0-9]*) W=1280 ;; esac
case "$H" in '' | *[!0-9]*) H=720 ;; esac

export HOME="${HOME:-/root}"
export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# Sin este archivo usa vidmode 4:3 fijo y no llena la ventana.
cat >"$HOME/.chromium-bsu" <<CFG
# Chromium B.S.U. configuration file
full_screen 0
screenWidth $W
screenHeight $H
gfxLevel 2
CFG

exec /usr/games/chromium-bsu --window "$@"
