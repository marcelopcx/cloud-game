#!/bin/sh
# Dusklight (Twilight Princess): arranca directo al juego con el disco de la imagen, render Vulkan.
set -eu

LOG="${CACHE_ROOT:-/cache}/dusklight.log"
exec >>"$LOG" 2>&1
echo "=== Dusklight $(date -Is) DISPLAY=${DISPLAY:-:99} STATION_SIZE=${STATION_SIZE:-1280x720} ==="

DISC=$(find /opt/dusklight/disc -maxdepth 1 -type f | head -1)
if [ -z "$DISC" ]; then
  echo "Dusklight: no hay disco en /opt/dusklight/disc" >&2
  exit 1
fi

# Config, saves y cache de pipelines de Dawn (~/.local/share/TwilitRealm/Dusklight).
export HOME="${CACHE_ROOT:-/cache}/dusklight-home"
DATA_DIR="$HOME/.local/share/TwilitRealm/Dusklight"
mkdir -p "$DATA_DIR"

# El loader borra /cache al salir: sin esto cada partida abre el diálogo de preset.
# Valores del preset "Dusklight" (src/dusk/ui/preset.cpp, applyPresetDusk) sin giroscopio.
if [ ! -f "$DATA_DIR/config.json" ]; then
  cat >"$DATA_DIR/config.json" <<'JSON'
{
    "backend.wasPresetChosen": true,
    "game.hideTvSettingsScreen": true,
    "game.noReturnRupees": true,
    "game.disableRupeeCutscenes": true,
    "game.noSwordRecoil": true,
    "game.fastClimbing": true,
    "game.noMissClimbing": true,
    "game.fastTears": true,
    "game.biggerWallets": true,
    "game.invertCameraXAxis": true,
    "game.invertFirstPersonYAxis": true,
    "game.no2ndFishForCat": true,
    "game.buttonFishing": true,
    "game.enableAchievementToasts": true,
    "game.enableControllerToasts": true,
    "game.enableQuickTransform": true,
    "game.instantSaves": true,
    "game.midnasLamentNonStop": true,
    "game.enableFrameInterpolation": 2,
    "game.sunsSong": true,
    "game.bloomMode": 2,
    "game.depthOfFieldMode": 2,
    "game.internalResolutionScale": 0,
    "game.shadowResolutionMultiplier": 4,
    "game.autoSave": true,
    "game.menuScalingMode": 2,
    "game.enhancedMapMenus": true,
    "game.enableMenuPointer": true
}
JSON
fi

# Sin Sentry: no pide consentimiento en cada partida ni manda reportes desde el servidor.
export BOREALIS_SENTRY_ENABLED=0

export SDL_VIDEO_WINDOW_POS=0,0
export SDL_VIDEO_CENTERED=0
# Aurora fija este hint por su cuenta y aborta si una variable de entorno lo pisa.
unset SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS

# --cvar no toca config.json. Con --dvd válido se salta la UI previa al juego.
# 60 FPS: es lo que captura la estación; más solo gasta GPU.
exec /opt/dusklight/bin/dusklight \
  --dvd "$DISC" \
  --backend vulkan \
  --cvar backend.checkForUpdates=false \
  --cvar backend.checkForModUpdates=false \
  --cvar video.enableFullscreen=false \
  --cvar video.maxFrameRate=60 \
  "$@"
