#!/usr/bin/env bash
# Build + levanta estación y cliente en un servidor Linux.
# Requiere: Docker Compose v2.
#
# Variables (o archivo .env en la raíz):
#   GAME            id del juego (default: supertuxkart)
#   ICE_HOST_IPS    IP(s) que el browser puede alcanzar (WebRTC)
#   PUBLIC_HOST     hostname/IP del certificado HTTPS del cliente
#   TLS_CERT        self (autofirmado) o acme (Let's Encrypt, 6 días con IP)
#   COMPOSE_FILES   override opcional, ej. "-f compose.yaml -f compose.gpu.yaml"
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

# shellcheck source=host-env.sh
source "$ROOT/scripts/host-env.sh"

GAME=${GAME:-supertuxkart}
COMPOSE_FILES=${COMPOSE_FILES:--f compose.yaml}

host_env_ensure
host_env_load_dotenv

if [[ ! -e /dev/uinput ]]; then
  echo "error: /dev/uinput no existe." >&2
  echo "       Jetson/Tegra: sudo ./scripts/setup-uinput.sh" >&2
  exit 1
fi

if [[ -z "${ICE_HOST_IPS:-}" ]]; then
  echo "error: ICE_HOST_IPS sigue vacío — editá .env o ejecutá: ./scripts/configure-external.sh" >&2
  exit 1
fi

"$ROOT/scripts/build-station.sh" "$GAME"

WAIT_ARGS=()
if docker_compose_supports_wait; then
  WAIT_ARGS=(--wait)
fi

EXTRA_UP=()
if [[ "${HOST_ENV_CHANGED:-0}" == 1 ]]; then
  echo "→ recreando game-station y game-client (ICE/HTTPS: ${PUBLIC_HOST})"
  EXTRA_UP=(--force-recreate game-station game-client)
fi

# shellcheck disable=SC2086
# --remove-orphans: baja las estaciones por juego de deploy-all.sh (mismo proyecto).
docker_compose $COMPOSE_FILES up --build -d --remove-orphans "${WAIT_ARGS[@]}" "${EXTRA_UP[@]}" "$@"
echo ""
host_env_print_access
