#!/bin/sh
# sm: config generada con el tamaño del display (4:3, ver manifest size).
set -eu

LOG="${CACHE_ROOT:-/cache}/sm.log"
exec >>"$LOG" 2>&1
echo "=== sm $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-960x720} ==="

SIZE="${STATION_SIZE:-960x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in '' | *[!0-9]*) W=960 ;; esac
case "$H" in '' | *[!0-9]*) H=720 ;; esac

CFG_DIR="${CACHE_ROOT:-/cache}/sm"
mkdir -p "$CFG_DIR"
CFG="$CFG_DIR/sm.ini"
sed -e "s/^WindowSize = .*/WindowSize = ${W}x${H}/" \
    -e "s/^Fullscreen = .*/Fullscreen = 0/" \
    /opt/sm/sm.ini >"$CFG"
echo "config -> $CFG (${W}x${H})"

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# saves/ se crea en el cwd.
cd /opt/sm
exec ./sm --config "$CFG" sm.smc "$@"
