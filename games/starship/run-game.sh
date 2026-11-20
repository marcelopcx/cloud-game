#!/bin/sh
# Port libultraship: binario y .o2r en /opt/<id>, config y saves en SHIP_HOME (/cache).
GAME_ID=starship
GAME_DIR=/opt/starship
GAME_BIN=Starship
GAME_CFG=starship.cfg.json

LOG="${CACHE_ROOT:-/cache}/${GAME_ID}.log"
exec >>"$LOG" 2>&1
echo "=== ${GAME_ID} start $(date -Is) DISPLAY=$DISPLAY STATION_SIZE=${STATION_SIZE:-?} ==="

SHIP_HOME="${SHIP_HOME:-${CACHE_ROOT:-/cache}/${GAME_ID}-ship}"
export SHIP_HOME
mkdir -p "$SHIP_HOME"
# Algunos ports buscan el .o2r del juego en SHIP_HOME y no junto al binario.
for o2r in "$GAME_DIR"/*.o2r; do
    [ -e "$SHIP_HOME/$(basename "$o2r")" ] || ln -s "$o2r" "$SHIP_HOME/"
done

SIZE="${STATION_SIZE:-1280x720}"
W="${SIZE%%x*}"
H="${SIZE#*x}"
case "$W" in ''|*[!0-9]*) W=1280 ;; esac
case "$H" in ''|*[!0-9]*) H=720 ;; esac

# Sin config la ventana abre 640x480 en (100,100) y la captura X11 queda con negro.
CFG="$SHIP_HOME/$GAME_CFG"
if [ ! -f "$CFG" ]; then
    cat >"$CFG" <<JSON
{
    "Window": {
        "Width": $W,
        "Height": $H,
        "PositionX": 0,
        "PositionY": 0,
        "Fullscreen": {
            "Enabled": true,
            "Width": $W,
            "Height": $H
        }
    }
}
JSON
    echo "ship config -> $CFG (${W}x${H})"
fi

export LD_LIBRARY_PATH="$GAME_DIR/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# Foco y ajuste de ventana: lo corre game-station (xfocus-game en bucle).
cd "$GAME_DIR"
exec "./$GAME_BIN" "$@"
