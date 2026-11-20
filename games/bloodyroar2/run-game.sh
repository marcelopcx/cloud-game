#!/bin/sh
# Bloody Roar II Recompiled: settings.toml junto al ejecutable (lo lee en cada arranque) y sin launcher.
set -eu

GAME_DIR=/opt/bloodyroar2
LOG="${CACHE_ROOT:-/cache}/bloodyroar2.log"
exec >>"$LOG" 2>&1
echo "=== bloodyroar2 $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-960x720} ==="

# fullscreen = 1: borderless a tamaño de Xvfb, el 4:3 queda centrado.
# "auto": cada jugador toma el primer mando libre (P1 el primero, P2 el segundo).
cat >"$GAME_DIR/settings.toml" <<'TOML'
[video]
renderer = "opengl"
fullscreen = 1

[controller]
p1_device = "auto"
p2_device = "auto"

[launcher]
skip_launcher = true
TOML

# El teclado se suma siempre a los mandos y por defecto maneja a todos los jugadores:
# lo dejamos solo en P1. Un jugador sin ninguna tecla hereda el mapa de P1, por eso P2 conserva R3 = F12.
{
    echo '[player1]'
    echo 'up = Up'; echo 'down = Down'; echo 'left = Left'; echo 'right = Right'
    echo 'cross = X'; echo 'circle = S'; echo 'square = Z'; echo 'triangle = A'
    echo 'l1 = Q'; echo 'r1 = W'; echo 'l2 = E'; echo 'r2 = R'; echo 'l3 = T'; echo 'r3 = Y'
    echo 'start = Return'; echo 'select = Right Shift'
    echo 'ls_up = Up'; echo 'ls_down = Down'; echo 'ls_left = Left'; echo 'ls_right = Right'
    echo
    echo '[player2]'
    for k in up down left right cross circle square triangle l1 r1 l2 r2 l3 start select \
             ls_up ls_down ls_left ls_right rs_up rs_down rs_left rs_right; do
        echo "$k = None"
    done
    echo 'r3 = F12'
} >"$GAME_DIR/keybinds.ini"

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0

# Rutas del disco y saves/ (memcards) relativas a game_us.toml.
cd "$GAME_DIR"
exec ./BloodyRoar2_Recompiled_USA --game game_us.toml --no-launcher \
    --disc "$GAME_DIR/disc/Bloody Roar II (USA).cue" --renderer opengl "$@"
