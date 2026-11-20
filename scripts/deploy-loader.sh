#!/usr/bin/env bash
# Levanta el modo Game Loader (una estación por partida) en un servidor Linux.
#
#   ./scripts/deploy-loader.sh                      # build de todos los juegos de games/ + loader + cliente
#   ./scripts/deploy-loader.sh supertux neverball   # solo construye esos juegos
#   ./scripts/deploy-loader.sh --no-build           # reusa las imágenes de juego ya construidas
#   ./scripts/deploy-loader.sh --down               # baja loader, cliente y estaciones creadas por el loader
#
# Completa .env (ICE_HOST_IPS, PUBLIC_HOST, CORS, LOADER_API_TOKEN, LOADER_GPU, DB_PASSWORD,
# ADMIN_PASSWORD) si falta algo,
# baja el stack de compose.yaml / deploy-all.sh (usa los mismos puertos 443/5173)
# y levanta compose.loader.yaml. Guía: docs/game-loader/README.md
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

# shellcheck source=host-env.sh
source "$ROOT/scripts/host-env.sh"

BUILD=1
DOWN=0
REQUESTED=()
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --down) DOWN=1 ;;
    -h | --help) sed -n 2,11p "$0"; exit 0 ;;
    *) REQUESTED+=("$arg") ;;
  esac
done

MANAGED=(--filter label=org.cloudgame.managed-by=game-loader)

if [[ "$DOWN" == 1 ]]; then
  docker_compose -f compose.loader.yaml down
  ids=$(docker ps -aq "${MANAGED[@]}")
  if [[ -n "$ids" ]]; then
    echo "→ borrando estaciones del loader"
    # shellcheck disable=SC2086
    docker rm -f -v $ids >/dev/null
  fi
  exit 0
fi

host_env_ensure
for key in LOADER_API_TOKEN DISPATCHER_API_TOKEN CLOUDGAME_JWT_SECRET; do
  if [[ -z "$(host_env_read_var "$key")" ]]; then
    echo "→ ${key} generado en .env"
    host_env_set_var "$key" "$(openssl rand -hex 24 2>/dev/null || head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    HOST_ENV_CHANGED=1
  fi
done
# Catálogo (db + game-crud + game-admin): contraseñas generadas una sola vez.
for key in DB_PASSWORD ADMIN_PASSWORD; do
  if [[ -z "$(host_env_read_var "$key")" ]]; then
    echo "→ ${key} generado en .env"
    host_env_set_var "$key" "$(openssl rand -hex 16 2>/dev/null || head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    HOST_ENV_CHANGED=1
  fi
done
if [[ "$(host_env_read_var LOADER_GPU)" != true ]] && docker info 2>/dev/null | grep -q nvidia; then
  echo "→ LOADER_GPU=true (runtime nvidia detectado)"
  host_env_set_var LOADER_GPU true
  HOST_ENV_CHANGED=1
fi
host_env_load_dotenv

if [[ "${LOADER_UINPUT:-true}" != false && ! -e /dev/uinput ]]; then
  echo "error: /dev/uinput no existe. Jetson/Tegra: sudo ./scripts/setup-uinput.sh" >&2
  echo "       (o LOADER_UINPUT=false en .env para correr sin gamepad)" >&2
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

READY=()
FAILED=()
if [[ "$BUILD" == 1 ]]; then
  docker build -t cloudgame/game-station-runtime:latest -f game-station/Dockerfile game-station
fi
for game in "${REQUESTED[@]}"; do
  if [[ ! -f "games/${game}/Dockerfile" ]]; then
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
    FAILED+=("$game")
    continue
  fi
  READY+=("$game")
done
if [[ ${#READY[@]} -eq 0 ]]; then
  echo "error: no hay ninguna imagen de juego (quitá --no-build o corré ./scripts/build-station.sh <juego>)" >&2
  exit 1
fi

# Las estaciones del loader sobreviven al despliegue y el dispatcher las adopta al arrancar: si
# su imagen se reconstruyó seguirían corriendo el runtime viejo. Se borran; el dispatcher las da
# por perdidas (health) y repone los mínimos con la imagen nueva. Corta las partidas en curso.
STALE=()
for id in $(docker ps -aq "${MANAGED[@]}"); do
  read -r name ref running < <(docker inspect -f '{{.Name}} {{.Config.Image}} {{.Image}}' "$id")
  current=$(docker image inspect -f '{{.Id}}' "$ref" 2>/dev/null || true)
  if [[ -n "$current" && "$current" != "$running" ]]; then
    STALE+=("$id")
    echo "→ estación ${name#/} con imagen vieja: se recrea"
  fi
done
if [[ ${#STALE[@]} -gt 0 ]]; then
  docker rm -f -v "${STALE[@]}" >/dev/null
fi

# compose.yaml y compose.multi.yaml (proyecto cloudgame) ocupan 443/5173 igual que el cliente del loader.
if [[ -n "$(docker ps -q --filter label=com.docker.compose.project=cloudgame)" ]]; then
  echo "→ bajando el stack cloudgame (compose.yaml / deploy-all.sh): comparte puertos con el loader"
  docker_compose -p cloudgame down
fi

WAIT_ARGS=()
if docker_compose_supports_wait; then
  WAIT_ARGS=(--wait)
fi
EXTRA_UP=()
if [[ "${HOST_ENV_CHANGED:-0}" == 1 ]]; then
  EXTRA_UP=(--force-recreate)
fi

# docker-entrypoint-initdb.d solo corre al crear el volumen: con una base existente, las tablas
# nuevas de game-crud/schemas no se crean y game-crud falla (ddl-auto=validate). Los .sql son
# idempotentes (IF NOT EXISTS / ON CONFLICT), así que se reaplican en cada despliegue.
docker_compose -f compose.loader.yaml up -d "${EXTRA_UP[@]}" db
echo "→ aplicando game-crud/schemas a la base"
# Por TCP: durante el initdb inicial el servidor temporal solo escucha en el socket.
PSQL='PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
for _ in $(seq 60); do
  docker_compose -f compose.loader.yaml exec -T db sh -c "$PSQL -c 'select 1'" >/dev/null 2>&1 && break
  sleep 1
done
docker_compose -f compose.loader.yaml exec -T db sh -c \
  "for f in /docker-entrypoint-initdb.d/*.sql; do $PSQL -f \"\$f\" || exit 1; done"
# Si game-crud venía cayéndose por el esquema, sigue marcado unhealthy y `up --wait` falla al
# instante aunque la base ya esté bien: se borra para que compose lo cree de nuevo.
crud=$(docker_compose -f compose.loader.yaml ps -aq game-crud 2>/dev/null || true)
if [[ -n "$crud" && "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$crud")" != healthy ]]; then
  echo "→ recreando game-crud (no estaba healthy)"
  docker_compose -f compose.loader.yaml rm -sf game-crud
fi

docker_compose -f compose.loader.yaml up -d --build "${WAIT_ARGS[@]}" "${EXTRA_UP[@]}"

echo ""
echo "Game Loader: http://127.0.0.1:8095  (máx. ${LOADER_MAX_CONTAINERS:-4} estaciones, puertos 8200+, GPU=${LOADER_GPU:-false})"
echo "Juegos con imagen: ${READY[*]}"
if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo "Sin imagen (build falló o no existe): ${FAILED[*]}"
fi
echo "Admin del catálogo: https://${PUBLIC_HOST:-localhost}/admin/  (administradores de la tabla ADMIN; el primero es ${ADMIN_USER:-admin} con ADMIN_PASSWORD de .env)"
echo "Documentación:      https://${PUBLIC_HOST:-localhost}/docs/  (mismo login)"
echo "Docs de las APIs:   https://${PUBLIC_HOST:-localhost}/api-docs/  (mismo login)"
echo "Logs: docker compose -f compose.loader.yaml logs -f game-loader"
echo "Bajar: ./scripts/deploy-loader.sh --down"
echo ""
host_env_print_access
