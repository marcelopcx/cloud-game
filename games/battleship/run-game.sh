#!/bin/sh
LOG="${CACHE_ROOT:-/cache}/battleship.log"
exec >>"$LOG" 2>&1
echo "=== BattleShip start $(date -Is) DISPLAY=$DISPLAY STATION_SIZE=${STATION_SIZE:-?} ==="

# libultraship guarda BattleShip.cfg.json en SHIP_HOME (NON_PORTABLE + Linux).
# Sin esto el juego abre 640x480 en (100,100) y la captura X11 queda con negro.
SHIP_HOME="${SHIP_HOME:-${CACHE_ROOT:-/cache}/battleship-ship}"
export SHIP_HOME
mkdir -p "$SHIP_HOME"

SIZE="${STATION_SIZE:-960x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in ''|*[!0-9]*) W=960 ;; esac
case "$H" in ''|*[!0-9]*) H=720 ;; esac

CFG="$SHIP_HOME/BattleShip.cfg.json"
TEMPLATE="/opt/battleship/BattleShip.cfg.json.in"
if [ -f "$TEMPLATE" ]; then
    sed -e "s/@WIDTH@/$W/g" -e "s/@HEIGHT@/$H/g" "$TEMPLATE" >"$CFG"
    echo "ship config -> $CFG (${W}x${H})"
else
    echo "WARN: falta $TEMPLATE" >&2
fi

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# Foco y ajuste de ventana: lo corre game-station (xfocus-game en bucle).
exec ./BattleShip
