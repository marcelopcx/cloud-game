#!/usr/bin/env bash
# Comprueba /dev/uinput: permisos, módulo y smoke test (crear pad + leer evento).
#
#   ./scripts/check-uinput.sh              # host Linux
#   ./scripts/check-uinput.sh --contenedor # game-station en Docker (si corre)
#   ./scripts/check-uinput.sh --solo-host  # no intenta el contenedor
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SMOKE_BIN="${TMPDIR:-/tmp}/cloudgame-uinput-smoke-$$"
SMOKE_INSTALLED=/usr/local/bin/cloudgame-uinput-smoke

resolve_smoke_src() {
  local f="$ROOT/game-station/docker/uinput-smoke.c"
  if [[ -f "$f" ]]; then
    printf '%s' "$f"
    return 0
  fi
  return 1
}

EN_CONTENEDOR=0
SOLO_HOST=0
for arg in "$@"; do
  case "$arg" in
    --contenedor | -c) EN_CONTENEDOR=1 ;;
    --solo-host) SOLO_HOST=1 ;;
    -h | --help)
      echo "Uso: $0 [--contenedor] [--solo-host]"
      exit 0
      ;;
  esac
done

# shellcheck source=host-env.sh
source "$ROOT/scripts/host-env.sh"

load_compose_files() {
  COMPOSE_FILES=${COMPOSE_FILES:--f compose.yaml}
  if [[ -f "$ROOT/.env" ]] && grep -qE '^COMPOSE_FILES=' "$ROOT/.env"; then
    COMPOSE_FILES=$(grep -E '^COMPOSE_FILES=' "$ROOT/.env" | head -1 | cut -d= -f2- | tr -d '"')
  fi
}

check_preflight() {
  local label=$1
  if [[ ! -e /dev/uinput ]]; then
    echo "✗ [$label] /dev/uinput no existe"
    echo "  Linux: sudo modprobe uinput && sudo chmod 666 /dev/uinput"
    echo "  Jetson: sudo ./scripts/setup-uinput.sh"
    return 1
  fi
  local mode perms
  mode=$(stat -c '%a' /dev/uinput 2>/dev/null || stat -f '%OLp' /dev/uinput 2>/dev/null || echo "?")
  perms=$(ls -l /dev/uinput)
  echo "✓ [$label] $perms (mode ${mode})"
  if [[ "$mode" != "666" && "$mode" != "664" && "$mode" != "660" ]]; then
    echo "  aviso: permisos restrictivos — el contenedor puede no escribir (chmod 666 o udev)"
  fi
  if command -v lsmod >/dev/null 2>&1; then
    if lsmod | grep -qE '^uinput\b'; then
      echo "✓ [$label] módulo uinput cargado"
    else
      echo "  aviso: módulo uinput no listado en lsmod (puede estar built-in)"
    fi
  fi
  if grep -q 'Cloud Game Pad' /proc/bus/input/devices 2>/dev/null; then
    echo "  info: ya hay un pad Cloud Game en input (partida activa o test previo)"
  fi
  return 0
}

build_smoke() {
  local src
  src=$(resolve_smoke_src) || {
    echo "error: falta game-station/docker/uinput-smoke.c" >&2
    return 1
  }
  if ! command -v cc >/dev/null 2>&1; then
    echo "error: instalá gcc (p. ej. sudo apt install build-essential)" >&2
    return 1
  fi
  # Estático: corre en el contenedor Jammy aunque el host tenga glibc más nueva.
  if cc -static -Wall -Wextra -O2 -o "$SMOKE_BIN" "$src" 2>/dev/null; then
    return 0
  fi
  cc -Wall -Wextra -O2 -o "$SMOKE_BIN" "$src"
}

run_smoke() {
  local label=$1
  echo "→ [$label] smoke test (pad virtual + EV_KEY)…"
  local code=0
  "$SMOKE_BIN" || code=$?
  if [[ "$code" -eq 0 ]]; then
    echo "✓ [$label] uinput escribe y el kernel entrega eventos"
    return 0
  fi
  # /dev/input/event* suele ser root:input 660; uinput basta con 666.
  if [[ "$label" == host && "$EUID" -ne 0 ]]; then
    echo "  reintentando con sudo (leer /dev/input/event*)…"
    if sudo "$SMOKE_BIN"; then
      echo "✓ [$label] uinput OK"
      echo "  tip: sudo usermod -aG input \"\$USER\" (cerrar sesión) para no usar sudo"
      return 0
    fi
  fi
  echo "✗ [$label] smoke test falló (código ${code})"
  return 1
}

run_on_host() {
  if [[ "$(uname -s)" != Linux ]]; then
    echo "omitido: host $(uname -s) — /dev/uinput solo existe en Linux"
    echo "  En el servidor: ./scripts/check-uinput.sh"
    echo "  O con stack levantado: ./scripts/check-uinput.sh --contenedor"
    return 2
  fi
  check_preflight host || return 1
  build_smoke || return 1
  run_smoke host
}

run_in_container() {
  load_compose_files
  local cid
  # shellcheck disable=SC2086
  cid=$(docker_compose $COMPOSE_FILES ps -q game-station 2>/dev/null | head -1 || true)
  if [[ -z "$cid" ]]; then
    echo "✗ contenedor game-station no está corriendo"
    echo "  Levantá el stack y repetí: ./scripts/check-uinput.sh --contenedor"
    return 1
  fi
  if ! docker exec "$cid" test -e /dev/uinput 2>/dev/null; then
    echo "✗ el contenedor no ve /dev/uinput"
    echo "  Recreá el contenedor:"
    echo "  docker compose ${COMPOSE_FILES} up -d --force-recreate game-station"
    return 1
  fi
  echo "✓ contenedor $cid ve /dev/uinput"
  if docker exec "$cid" test -x "$SMOKE_INSTALLED" 2>/dev/null; then
    echo "→ [contenedor] smoke ($SMOKE_INSTALLED)…"
    if docker exec "$cid" "$SMOKE_INSTALLED"; then
      echo "✓ [contenedor] uinput OK dentro de game-station"
      return 0
    fi
    echo "✗ [contenedor] smoke test falló"
    return 1
  fi
  echo "  aviso: imagen sin $SMOKE_INSTALLED — rebuild runtime o binario estático del host"
  build_smoke || return 1
  docker cp "$SMOKE_BIN" "$cid:/tmp/cloudgame-uinput-smoke" >/dev/null
  docker exec "$cid" chmod +x /tmp/cloudgame-uinput-smoke
  echo "→ [contenedor] smoke (/tmp/cloudgame-uinput-smoke estático)…"
  if docker exec "$cid" /tmp/cloudgame-uinput-smoke; then
    echo "✓ [contenedor] uinput OK (rebuild runtime para binario embebido)"
    docker exec "$cid" rm -f /tmp/cloudgame-uinput-smoke >/dev/null 2>&1 || true
    return 0
  fi
  echo "✗ [contenedor] smoke test falló"
  echo "  rebuild: docker compose -f compose.yaml -f compose.gpu.yaml build runtime --no-cache"
  return 1
}

cleanup() {
  rm -f "$SMOKE_BIN" 2>/dev/null || true
}
trap cleanup EXIT

main() {
  echo "=== check-uinput ==="
  local rc=0

  if [[ "$EN_CONTENEDOR" -eq 1 ]]; then
    run_in_container || rc=1
    exit "$rc"
  fi

  run_on_host || rc=$?
  if [[ "$rc" -eq 2 ]]; then
    if [[ "$SOLO_HOST" -eq 0 ]] && docker info >/dev/null 2>&1; then
      echo ""
      echo "→ probando dentro del contenedor…"
      run_in_container && rc=0 || true
    fi
    exit "$rc"
  fi

  if [[ "$rc" -eq 0 && "$SOLO_HOST" -eq 0 ]] && docker info >/dev/null 2>&1; then
    load_compose_files
    # shellcheck disable=SC2086
    if docker_compose $COMPOSE_FILES ps -q game-station 2>/dev/null | grep -q .; then
      echo ""
      run_in_container || rc=1
    fi
  fi

  exit "$rc"
}

main
