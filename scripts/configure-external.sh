#!/usr/bin/env bash
# Fuerza IP/HTTPS/WebRTC para acceso desde fuera del host (LAN o internet).
# Uso: ./scripts/configure-external.sh [IP_o_hostname]
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

# shellcheck source=host-env.sh
source "$ROOT/scripts/host-env.sh"

if [[ ! -f .env ]]; then
  cp .env.example .env
fi

if [[ -n "${1:-}" ]]; then
  ip=$1
  host_env_set_var ICE_HOST_IPS "$ip"
  host_env_set_var PUBLIC_HOST "$ip"
  host_env_sync_cors "$ip"
  if host_env_is_private_ip "$ip"; then
    host_env_set_var ICE_HOST_POLICY all
  else
    host_env_set_var ICE_HOST_POLICY public
  fi
  echo "→ configurado acceso externo con host ${ip}"
else
  host_env_ensure 1
fi

echo ""
host_env_print_access
echo ""
echo "Relevá el stack: ./scripts/deploy.sh"
