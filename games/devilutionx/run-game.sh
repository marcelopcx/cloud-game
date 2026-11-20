#!/bin/sh
# DevilutionX: ventana (Xvfb sin WM); xfocus-game la estira al display. Sin DIABDAT.MPQ arranca el shareware.
set -eu

LOG="${CACHE_ROOT:-/cache}/devilutionx.log"
exec >>"$LOG" 2>&1
echo "=== DevilutionX $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

CONFIG_DIR="${CACHE_ROOT:-/cache}/devilutionx"
mkdir -p "$CONFIG_DIR/saves"

# Upscale + Fit to Screen: resolución interna con el aspecto del display y ventana redimensionable.
if [ ! -f "$CONFIG_DIR/diablo.ini" ]; then
  cat >"$CONFIG_DIR/diablo.ini" <<INI
[Graphics]
Fullscreen=0
Upscale=1
Fit to Screen=1
Integer Scaling=0
Vertical Sync=0
Frame Rate Control=2

[Diablo]
Intro=0

[Game Mode]
Diablo Intro=0
INI
fi

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

exec /opt/devilutionx/devilutionx \
  --data-dir /opt/devilutionx \
  --config-dir "$CONFIG_DIR" \
  --save-dir "$CONFIG_DIR/saves" \
  "$@"
