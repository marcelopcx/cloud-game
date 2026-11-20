#!/usr/bin/env bash
# Detección de IP + .env (ICE, HTTPS, CORS) y wrapper de Docker Compose.
# Uso: source scripts/host-env.sh
docker_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
    return
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
    return
  fi
  cat >&2 <<'EOF'
No se encontró Docker Compose.

Ubuntu / Jetson:
  sudo apt update
  sudo apt install -y docker-compose-v2
  docker compose version

EOF
  return 127
}

docker_compose_supports_wait() {
  docker compose version >/dev/null 2>&1
}

host_env_detect_ip() {
  ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}' \
    || hostname -I 2>/dev/null | awk '{print $1}' \
    || echo "127.0.0.1"
}

host_env_is_local_placeholder() {
  local v=${1:-}
  [[ -z "$v" || "$v" == "localhost" || "$v" == "127.0.0.1" ]]
}

host_env_is_private_ip() {
  local ip=$1
  case "$ip" in
    10.* | 192.168.* | 127.* | 169.254.*) return 0 ;;
    172.*)
      local second=${ip#172.}
      second=${second%%.*}
      [[ "$second" -ge 16 && "$second" -le 31 ]] && return 0
      ;;
  esac
  return 1
}

host_env_read_var() {
  local key=$1
  grep -E "^${key}=" .env 2>/dev/null | head -1 | cut -d= -f2- | tr -d '"' || true
}

host_env_set_var() {
  local key=$1 val=$2
  if grep -q "^${key}=" .env 2>/dev/null; then
    sed -i "s|^${key}=.*|${key}=${val}|" .env
  else
    echo "${key}=${val}" >> .env
  fi
}

host_env_sync_cors() {
  local ip=$1
  host_env_set_var CORS_ORIGINS \
    "https://${ip},http://${ip}:5173,http://127.0.0.1:5173,https://localhost,http://localhost:5173"
}

# Completa ICE_HOST_IPS, PUBLIC_HOST, CORS e ICE_HOST_POLICY en .env si faltan o siguen en localhost.
# Exporta HOST_ENV_CHANGED=1 si modificó el archivo.
host_env_ensure() {
  HOST_ENV_CHANGED=0
  local force=${1:-0}

  if [[ ! -f .env ]]; then
    cp .env.example .env
    HOST_ENV_CHANGED=1
    echo "→ creado .env desde .env.example"
  fi

  local ip detected
  detected=$(host_env_detect_ip)
  if host_env_is_local_placeholder "$detected"; then
    echo "aviso: no se detectó IP de salida — configurá ICE_HOST_IPS y PUBLIC_HOST a mano en .env" >&2
    return 0
  fi

  local ice public policy
  ice=$(host_env_read_var ICE_HOST_IPS)
  public=$(host_env_read_var PUBLIC_HOST)

  if [[ "$force" == 1 ]] || host_env_is_local_placeholder "$ice"; then
    echo "→ ICE_HOST_IPS=${detected}"
    host_env_set_var ICE_HOST_IPS "$detected"
    HOST_ENV_CHANGED=1
  fi

  if [[ "$force" == 1 ]] || host_env_is_local_placeholder "$public"; then
    echo "→ PUBLIC_HOST=${detected}"
    host_env_set_var PUBLIC_HOST "$detected"
    host_env_sync_cors "$detected"
    HOST_ENV_CHANGED=1
  elif host_env_is_local_placeholder "$(host_env_read_var CORS_ORIGINS)" \
    || ! grep -qF "https://${public}" .env 2>/dev/null; then
    host_env_sync_cors "${public:-$detected}"
    HOST_ENV_CHANGED=1
  fi

  policy=$(host_env_read_var ICE_HOST_POLICY)
  if [[ -z "$policy" ]]; then
    if host_env_is_private_ip "$detected"; then
      host_env_set_var ICE_HOST_POLICY all
    else
      host_env_set_var ICE_HOST_POLICY public
    fi
    HOST_ENV_CHANGED=1
  fi

  if [[ ! -f .env ]] || ! grep -qE '^COMPOSE_FILES=' .env 2>/dev/null; then
    if docker info 2>/dev/null | grep -q nvidia; then
      echo '→ COMPOSE_FILES="-f compose.yaml -f compose.gpu.yaml" (runtime nvidia detectado)'
      echo 'COMPOSE_FILES="-f compose.yaml -f compose.gpu.yaml"' >> .env
      HOST_ENV_CHANGED=1
    fi
  fi
}

host_env_load_dotenv() {
  COMPOSE_FILES=${COMPOSE_FILES:--f compose.yaml}
  if [[ ! -f .env ]]; then
    return 0
  fi
  if grep -qE '^COMPOSE_FILES=' .env; then
    COMPOSE_FILES=$(grep -E '^COMPOSE_FILES=' .env | head -1 | cut -d= -f2- | tr -d '"')
  fi
  set -a
  # shellcheck disable=SC1091
  source <(grep -vE '^COMPOSE_FILES=' .env)
  set +a
}

host_env_print_access() {
  local host=${PUBLIC_HOST:-$(host_env_detect_ip)}
  local ice=${ICE_HOST_IPS:-$host}
  cat <<EOF
Acceso desde otro equipo (misma red o con puertos abiertos):
  UI (HTTPS, gamepad):  https://${host}/
  UI (HTTP, sin mando): http://${host}:5173

WebRTC ICE anunciado: ${ice}
Firewall en el host: TCP 80 (TLS_CERT=acme), 443, 5173; UDP amplio (p. ej. 10000-65535) hacia esta máquina.
Si el browser está fuera de la LAN, PUBLIC_HOST/ICE_HOST_IPS deben ser la IP pública enrutable.
EOF
}
