#!/bin/sh
# zelda3: config generada según STATION_SIZE (16:9 usa el modo panorámico del port).
set -eu

LOG="${CACHE_ROOT:-/cache}/zelda3.log"
exec >>"$LOG" 2>&1
echo "=== zelda3 $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

SIZE="${STATION_SIZE:-1280x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in '' | *[!0-9]*) W=1280 ;; esac
case "$H" in '' | *[!0-9]*) H=720 ;; esac
# 16:9 → pantalla extendida; cualquier otro aspecto → 4:3 original.
if [ $((W * 9)) -eq $((H * 16)) ]; then ASPECT=16:9; else ASPECT=4:3; fi

CFG_DIR="${CACHE_ROOT:-/cache}/zelda3"
mkdir -p "$CFG_DIR"
CFG="$CFG_DIR/zelda3.ini"
sed -e "s/^ExtendedAspectRatio = .*/ExtendedAspectRatio = $ASPECT/" \
    -e "s/^WindowSize = .*/WindowSize = ${W}x${H}/" \
    -e "s/^Fullscreen = .*/Fullscreen = 0/" \
    -e "s/^EnableMSU = .*/EnableMSU = false/" \
    /opt/zelda3/zelda3.ini >"$CFG"
echo "config -> $CFG (${W}x${H}, $ASPECT)"

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# Assets y saves/ se buscan en el cwd.
cd /opt/zelda3
exec ./zelda3 --config "$CFG" "$@"
