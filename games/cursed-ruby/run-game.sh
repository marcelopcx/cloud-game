#!/bin/sh
# Cursed Ruby (pack de Godot 3.4.3 sobre el runtime 3.6): ventana del tamaño del display; xfocus-game le da foco.
set -eu

LOG="${CACHE_ROOT:-/cache}/cursed-ruby.log"
exec >>"$LOG" 2>&1
echo "=== Cursed Ruby $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

# user:// (saves y config) va a ~/.local/share/godot.
export HOME="${CACHE_ROOT:-/cache}/cursed-ruby-home"
mkdir -p "$HOME"

exec /opt/cursed-ruby/godot \
  --main-pack /opt/cursed-ruby/game.pck \
  --audio-driver PulseAudio \
  --windowed \
  --resolution "${STATION_SIZE:-1280x720}" \
  --position 0,0 \
  "$@"
