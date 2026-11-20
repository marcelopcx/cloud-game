#!/usr/bin/env bash
# Una estación (contenedor) por juego + un solo cliente nginx.
#
#   ./scripts/deploy-all.sh                      # todos los juegos de games/
#   ./scripts/deploy-all.sh supertux neverball   # solo esos
#   ./scripts/deploy-all.sh --no-build           # reusa imágenes ya construidas
#
# Genera compose.multi.yaml y .multi/stations.conf (no versionados). Puerto y
# display salen del orden alfabético de games/, así que no cambian al levantar
# un subconjunto:  juego i → HTTP 8100+i, DISPLAY :(100+i).
# El cliente habla con /stations/<gameId>/ y nginx lo manda al puerto de ese juego.
# Los pads uinput no se mezclan: sin /dev/input del host, cada estación crea
# solo los nodos de sus pads (ver PadNodes.java).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

# shellcheck source=host-env.sh
source "$ROOT/scripts/host-env.sh"

BUILD=1
REQUESTED=()
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    -h | --help) sed -n 2,14p "$0"; exit 0 ;;
    *) REQUESTED+=("$arg") ;;
  esac
done

host_env_ensure
host_env_load_dotenv

if [[ ! -e /dev/uinput ]]; then
  echo "error: /dev/uinput no existe. Jetson/Tegra: sudo ./scripts/setup-uinput.sh" >&2
  exit 1
fi
if [[ -z "${ICE_HOST_IPS:-}" ]]; then
  echo "error: ICE_HOST_IPS sigue vacío — editá .env o ejecutá: ./scripts/configure-external.sh" >&2
  exit 1
fi

ALL_GAMES=()
for dir in games/*/; do
  [[ -f "$dir/Dockerfile" ]] && ALL_GAMES+=("$(basename "$dir")")
done
if [[ ${#REQUESTED[@]} -eq 0 ]]; then
  REQUESTED=("${ALL_GAMES[@]}")
fi

slot_of() {
  local i=1 g
  for g in "${ALL_GAMES[@]}"; do
    [[ "$g" == "$1" ]] && { echo "$i"; return 0; }
    i=$((i + 1))
  done
  return 1
}

GAMES=()
FAILED=()
if [[ "$BUILD" == 1 ]]; then
  docker build -t cloudgame/game-station-runtime:latest -f game-station/Dockerfile game-station
fi
for game in "${REQUESTED[@]}"; do
  if ! slot_of "$game" >/dev/null; then
    echo "juego desconocido: $game (no existe games/${game}/Dockerfile)" >&2
    exit 1
  fi
  if [[ "$BUILD" == 1 ]]; then
    echo "→ build ${game}"
    if ! docker build -t "cloudgame/game-station-${game}" \
      --build-arg RUNTIME_IMAGE=cloudgame/game-station-runtime:latest "games/${game}"; then
      echo "aviso: falló el build de ${game}; se omite" >&2
      FAILED+=("$game")
      continue
    fi
  elif ! docker image inspect "cloudgame/game-station-${game}" >/dev/null 2>&1; then
    echo "aviso: no existe la imagen cloudgame/game-station-${game}; se omite (quitá --no-build)" >&2
    FAILED+=("$game")
    continue
  fi
  GAMES+=("$game")
done
if [[ ${#GAMES[@]} -eq 0 ]]; then
  echo "error: ningún juego para levantar" >&2
  exit 1
fi

GPU=0
[[ "${COMPOSE_FILES:-}" == *compose.gpu.yaml* ]] && GPU=1

mkdir -p .multi
{
  echo "# Generado por scripts/deploy-all.sh — no editar."
  echo "map \$station_game \$station_port {"
  echo "    default \"\";"
  for game in "${GAMES[@]}"; do
    echo "    ${game} $((8100 + $(slot_of "$game")));"
  done
  echo "}"
} > .multi/stations.conf

{
  cat <<'EOF'
# Generado por scripts/deploy-all.sh — no editar (se regenera en cada deploy).
# Mismo proyecto que compose.yaml: `up --remove-orphans` baja el modo de un solo juego.
name: cloudgame

x-station: &station
  pull_policy: never
  network_mode: host
  # Sin /dev/input del host: la estación crea acá solo los nodos de sus pads.
  devices:
    - /dev/uinput
  device_cgroup_rules:
    - "c 13:* rmw"
  init: true
  restart: unless-stopped
  healthcheck:
    test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:$${STATION_HTTP_PORT}/health"]
    interval: 5s
    timeout: 3s
    retries: 24
    start_period: 40s

x-station-env: &station-env
  CORS_ORIGINS: ${CORS_ORIGINS:-http://localhost:5173,http://127.0.0.1:5173,https://localhost}
  ICE_HOST_POLICY: ${ICE_HOST_POLICY:-public}
  ICE_HOST_IPS: ${ICE_HOST_IPS:-}
  ICE_SERVERS: ${ICE_SERVERS:-stun:stun.l.google.com:19302}
  STATION_SIZE: ${STATION_SIZE:-1280x720}
  STATION_STREAM_SIZE: ${STATION_STREAM_SIZE:-}
  STATION_STREAM_SCALER: ${STATION_STREAM_SCALER:-bicubic}
  STATION_FPS: ${STATION_FPS:-60}
  STATION_VIDEO_BITRATE: ${STATION_VIDEO_BITRATE:-5000000}
  LIBRARY_ROOT: /opt/station-library
  CACHE_ROOT: /cache
  IDLE_TIMEOUT_S: ${IDLE_TIMEOUT_S:-300}
EOF
  if [[ "$GPU" == 1 ]]; then
    cat <<'EOF'
  NVIDIA_VISIBLE_DEVICES: all
  NVIDIA_DRIVER_CAPABILITIES: all
  __GLX_VENDOR_LIBRARY_NAME: nvidia
EOF
  fi
  echo ""
  echo "services:"
  for game in "${GAMES[@]}"; do
    slot=$(slot_of "$game")
    cat <<EOF
  station-${game}:
    <<: *station
    image: cloudgame/game-station-${game}
EOF
    if [[ "$GPU" == 1 ]]; then
      cat <<'EOF'
    runtime: nvidia
    devices:
      - /dev/uinput
      - /dev/dri
EOF
    fi
    cat <<EOF
    environment:
      <<: *station-env
      STATION_ID: station-${game}
      STATION_HTTP_PORT: "$((8100 + slot))"
      STATION_DISPLAY: ":$((100 + slot))"
    volumes:
      - cache-${game}:/cache

EOF
  done
  cat <<'EOF'
  game-client:
    build:
      context: ./game-client
      args:
        VITE_STATION_URL: /station
        VITE_STATIONS_URL: /stations
        VITE_ICE_SERVERS: ${ICE_SERVERS:-stun:stun.l.google.com:19302}
        VITE_GAME_ID: ${GAME:-supertuxkart}
    image: cloudgame/game-client:latest
    network_mode: host
    environment:
      STATION_UPSTREAM: http://127.0.0.1:8090
      NGINX_ENVSUBST_FILTER: STATION_UPSTREAM|DISPATCHER_UPSTREAM
      PUBLIC_HOST: ${PUBLIC_HOST:-localhost}
      TLS_CERT: ${TLS_CERT:-self}
      ACME_EMAIL: ${ACME_EMAIL:-}
    volumes:
      - ./.multi/stations.conf:/etc/nginx/conf.d/stations.conf:ro
      - ./.acme:/etc/nginx/acme
    init: true
    restart: unless-stopped

volumes:
EOF
  for game in "${GAMES[@]}"; do
    echo "  cache-${game}:"
  done
} > compose.multi.yaml

WAIT_ARGS=()
if docker_compose_supports_wait; then
  WAIT_ARGS=(--wait)
fi
# --build solo reconstruye el cliente (las estaciones no tienen build:).
if ! docker_compose -f compose.multi.yaml up -d --build --remove-orphans "${WAIT_ARGS[@]}"; then
  echo "aviso: alguna estación no quedó healthy — docker compose -f compose.multi.yaml ps" >&2
fi

echo ""
echo "Estaciones:"
for game in "${GAMES[@]}"; do
  slot=$(slot_of "$game")
  printf '  %-16s http://127.0.0.1:%d  DISPLAY=:%d  → /stations/%s/\n' \
    "$game" $((8100 + slot)) $((100 + slot)) "$game"
done
if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo "Omitidos (build/imagen): ${FAILED[*]}"
fi
echo ""
host_env_print_access
