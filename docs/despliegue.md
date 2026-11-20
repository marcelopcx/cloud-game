# Despliegue

Cloud Game se despliega en un **servidor Linux** con Docker Compose. El modo por defecto
levanta el **Game Loader** y el cliente web; el loader crea un contenedor de estación por partida.

## Requisitos

| Requisito | Detalle |
| --- | --- |
| SO | Linux. La estación usa Xvfb, PulseAudio y `/dev/uinput`. |
| Docker | Docker Engine con **Compose v2** (`docker compose`). `host-env.sh` también acepta `docker-compose`. |
| `/dev/uinput` | Obligatorio: los scripts abortan si no existe (salvo `LOADER_UINPUT=false`). En Jetson/Tegra, ver [uinput](#uinput). |
| GPU NVIDIA | Opcional. Se usa si Docker tiene el runtime `nvidia`. |
| Red | IP alcanzable por los navegadores. TCP 443/5173 (y 80 con `TLS_CERT=acme`) y UDP amplio abiertos. |

## Modos de despliegue

Los tres usan los puertos 443/5173 del cliente, así que se levanta **uno solo** a la vez.
`deploy-loader.sh` baja el stack de los otros dos si lo encuentra corriendo.

| Modo | Script | Compose | Cuándo usarlo |
| --- | --- | --- | --- |
| **Game Loader** (por defecto) | `scripts/deploy-loader.sh` | `compose.loader.yaml` | Producción. Una estación por partida, creada al darle Jugar y destruida al salir. Es el que usa el CI/CD. |
| Una estación fija | `scripts/deploy.sh` | `compose.yaml` (+ `compose.gpu.yaml`) | Probar un solo juego (`GAME=`) con la estación siempre levantada en `:8090`. |
| Una estación por juego | `scripts/deploy-all.sh` | `compose.multi.yaml` (generado) | Todas las estaciones levantadas a la vez (`8100+i`). Consume memoria aunque nadie juegue. |

## Despliegue rápido (Game Loader)

```bash
cp .env.example .env                       # opcional: el script lo crea si falta
./scripts/deploy-loader.sh                 # build de todos los juegos + loader + cliente
./scripts/deploy-loader.sh supertux soh    # solo reconstruye esos juegos
./scripts/deploy-loader.sh --no-build      # reusa las imágenes de juego existentes
./scripts/deploy-loader.sh --down          # baja loader, cliente y estaciones creadas
```

Además de lo que hace `deploy.sh` con `.env` (ver abajo), `deploy-loader.sh`:

- Genera `LOADER_API_TOKEN`, `DB_PASSWORD` y `ADMIN_PASSWORD` si están vacíos y pone `LOADER_GPU=true` si detecta el runtime `nvidia`.
- Construye la imagen base y la de cada juego. Si el build de un juego falla (p. ej. falta la ROM), lo informa y sigue con el resto.
- Levanta `compose.loader.yaml` con `--wait`.

Al terminar imprime las URLs de acceso:

```
UI (HTTPS, gamepad):  https://<PUBLIC_HOST>/
UI (HTTP, sin mando): http://<PUBLIC_HOST>:5173
```

### Catálogo (base de datos + admin)

`compose.loader.yaml` también levanta el catálogo de juegos:

| Servicio | Escucha | Rol |
| --- | --- | --- |
| `db` | `127.0.0.1:5433` | PostgreSQL 16, volumen `db-data`. `game-crud/schemas/*.sql` se aplica **solo al crear el volumen**. |
| `game-crud` | `127.0.0.1:8096` | API del catálogo; OpenAPI en `/v3/api-docs` (sin Swagger UI). |
| `game-admin` | `127.0.0.1:8097` | Panel del catálogo; su nginx reenvía `/api/` a `game-crud`. |
| `game-api` | `127.0.0.1:8098` | Documentación [Scalar](../game-api/README.md) de loader, estación y `game-crud`. |
| `game-docs` | `127.0.0.1:8099` | [Sitio de documentación](game-docs/README.md) (Starlight) construido desde `docs/`. |

Nada de eso se abre al firewall: el nginx de `game-client` publica el panel en
`https://<PUBLIC_HOST>/admin/`, la documentación en `https://<PUBLIC_HOST>/docs/` y la referencia de
APIs en `https://<PUBLIC_HOST>/api-docs/`, las tres con basic auth contra la tabla `ADMIN` de
game-crud. El primer administrador es `ADMIN_USER` (default `admin`) con `ADMIN_PASSWORD` de `.env`;
los demás se crean en el panel (pestaña **Administradores**).

Un cambio de esquema posterior al primer deploy **no** se aplica solo: correrlo a mano, p. ej.
`docker compose -f compose.loader.yaml exec -T db psql -U cloudgame -d cloud_game < cambio.sql`.

Detalles del loader (API, slots, seguridad): [Game Loader](game-loader/README.md).

## Una estación fija (`deploy.sh`)

```bash
./scripts/deploy.sh                 # GAME de .env (default supertuxkart)
GAME=battleship ./scripts/deploy.sh
```

## Qué hace `deploy.sh`

1. **Completa `.env`** (`host_env_ensure` en `scripts/host-env.sh`):
   - Si no existe, lo copia desde `.env.example`.
   - Detecta la IP de salida (`ip route get 1.1.1.1` o `hostname -I`).
   - Si `ICE_HOST_IPS` o `PUBLIC_HOST` están vacíos o en `localhost`/`127.0.0.1`, los reemplaza por esa IP y regenera `CORS_ORIGINS`.
   - Si `ICE_HOST_POLICY` está vacío: `all` para una IP privada, `public` para una pública.
   - Si no hay `COMPOSE_FILES` y `docker info` reporta `nvidia`, agrega `COMPOSE_FILES="-f compose.yaml -f compose.gpu.yaml"`.
2. **Valida** que exista `/dev/uinput` y que `ICE_HOST_IPS` no esté vacío.
3. **Construye** la imagen base y la del juego (`scripts/build-station.sh $GAME`).
4. **Levanta** el stack con `docker compose $COMPOSE_FILES up --build -d --wait`. Si el paso 1 cambió `.env`, fuerza la recreación de `game-station` y `game-client`.

## Variables de entorno (`.env`)

| Variable | Default | Descripción |
| --- | --- | --- |
| `GAME` | `supertuxkart` | Carpeta de `games/` que se construye y despliega. |
| `STATION_ID` | `station-1` | Identificador en `/health`. |
| `ICE_HOST_IPS` | autodetectada | IP(s) que el navegador puede alcanzar. La estación solo anuncia esas IPs como candidatos `host`. |
| `PUBLIC_HOST` | autodetectada | Host/IP de acceso. Es el CN del certificado autofirmado. |
| `ICE_HOST_POLICY` | `all` (`.env.example`) | `all` para LAN; `public` descarta candidatos privados. |
| `ICE_SERVERS` | `stun:stun.l.google.com:19302` | STUN/TURN, para la estación y el cliente. |
| `CORS_ORIGINS` | generado | Orígenes permitidos por la estación. |
| `STATION_FPS` | `60` | FPS de captura. |
| `STATION_VIDEO_BITRATE` | `5000000` | Bitrate máximo de video (bps). |
| `STATION_SIZE` | `1280x720` | Resolución del display. Sobrescribe la de la imagen del juego. |
| `STATION_STREAM_SIZE` | vacío | Resolución del video enviado (ej. `854x480`). Vacío = `STATION_SIZE`. El cliente lo agranda con FSR. |
| `STATION_STREAM_SCALER` | `bicubic` | Filtro de ffmpeg para reducir: `bicubic`, `lanczos`, `bilinear`, `area`, `fast_bilinear`. |
| `STATION_DISPLAY` | `:99` | Display de Xvfb. |
| `IDLE_TIMEOUT_S` | `300` | Segundos sin input antes de cortar la partida. |
| `COMPOSE_FILES` | `-f compose.yaml` | Archivos de compose que usa `deploy.sh`. |
| `TLS_CERT` | `self` | `self`: certificado autofirmado. `acme`: Let's Encrypt para `PUBLIC_HOST` (ver [HTTPS](#https)). |
| `ACME_EMAIL` | vacío | Contacto opcional de la cuenta ACME. |
| `LOADER_API_TOKEN` | generado | Token entre nginx y el loader. `deploy-loader.sh` lo genera si falta. |
| `LOADER_MAX_CONTAINERS` | `4` | Estaciones simultáneas (puertos `8200+`, displays `:200+`). |
| `LOADER_GPU` | `false` | Pasa la GPU NVIDIA a las estaciones. `deploy-loader.sh` lo activa si detecta `nvidia`. |
| `LOADER_UINPUT` | `true` | `false` solo en Docker Desktop (Mac) sin `/dev/uinput`. |
| `DB_PORT` / `DB_NAME` / `DB_USER` | `5433` / `cloud_game` / `cloudgame` | PostgreSQL del catálogo (solo loopback). |
| `DB_PASSWORD` | generado | Contraseña de PostgreSQL. Solo aplica al crear el volumen `db-data`. |
| `ADMIN_USER` / `ADMIN_PASSWORD` | `admin` / generado | Primer administrador de `https://<PUBLIC_HOST>/admin/`, `/docs/` y `/api-docs/`; game-crud lo crea solo si la tabla `ADMIN` está vacía. |
| `CLOUDGAME_JWT_SECRET` | generado | Secreto compartido por game-crud y game-dispatcher para la sesión de jugadores y los permisos de save. Si cambia, se invalidan todas las sesiones. |
| `SAVES_MAX_MB` | `256` | Tope del save de un usuario en un juego. Los saves viven en el volumen `player-saves`: incluilo en los backups. |
| `IMAGES_MAX_MB` | `25` | Tope de una imagen del catálogo subida desde `/admin/`. Las imágenes viven en el volumen `catalog-images`: incluilo en los backups junto con la base. |

La lista completa de variables de la estación está en [Game Station](game-station/README.md#configuración).

## Archivos de Compose

| Archivo | Uso |
| --- | --- |
| [`compose.yaml`](../compose.yaml) | Stack base: `runtime` (imagen base, sale al terminar), `game-station` y `game-client`, ambos con `network_mode: host`. |
| [`compose.loader.yaml`](../compose.loader.yaml) | Modo por defecto: `game-dispatcher` (reparte estaciones, pool mínimo, inactividad), `game-loader` (con `/var/run/docker.sock`) y `game-client` compilado con `VITE_DISPATCHER_URL=/dispatcher`. El dispatcher pide las estaciones al loader; el browser no habla con el loader. Además el catálogo: `db`, `game-crud` y `game-admin`, y la documentación `game-api` y `game-docs`. |
| `compose.multi.yaml` | Generado por `deploy-all.sh` (no versionado), junto con `.multi/stations.conf` para nginx. |
| [`compose.gpu.yaml`](../compose.gpu.yaml) | Override para NVIDIA: `runtime: nvidia`, `/dev/dri`, `NVIDIA_VISIBLE_DEVICES=all`, `NVIDIA_DRIVER_CAPABILITIES=all`, `__GLX_VENDOR_LIBRARY_NAME=nvidia`. |

Detalles de `game-station` en `compose.yaml`:

- `devices: /dev/uinput`, volumen `/dev/input:/dev/input` y `device_cgroup_rules: "c 13:* rmw"` para los pads virtuales.
- Volumen `station-cache` en `/cache` (cache de `prepare` y logs de los juegos).
- Healthcheck `curl -fsS http://127.0.0.1:8090/health` cada 5 s (24 reintentos, `start_period` de 40 s). `game-client` espera a que esté `healthy`.
- `restart: unless-stopped`.

## Scripts

| Script | Uso |
| --- | --- |
| `scripts/deploy-loader.sh` | Modo Game Loader (descripto arriba). `--no-build`, `--down` o una lista de juegos. |
| `scripts/deploy.sh` | Una estación fija (descripto arriba). Pasa argumentos extra a `docker compose up`. |
| `scripts/deploy-all.sh` | Una estación por juego. `--no-build` o una lista de juegos. Genera `compose.multi.yaml`. |
| `scripts/build-station.sh <id>` | Construye `cloudgame/game-station-runtime:latest` y `cloudgame/game-station-<id>`. |
| `scripts/configure-external.sh [IP]` | Fuerza `ICE_HOST_IPS`, `PUBLIC_HOST`, `CORS_ORIGINS` e `ICE_HOST_POLICY` para una IP u hostname (o la detectada). Después hay que ejecutar `deploy.sh`. |
| `scripts/host-env.sh` | Funciones compartidas (detección de IP, edición de `.env`, wrapper de Compose). Se usa con `source`. |
| `scripts/setup-uinput.sh` | `sudo`. Compila e instala `uinput.ko` en kernels sin `CONFIG_INPUT_UINPUT` (por ejemplo Jetson `-tegra`), lo carga al boot y deja `/dev/uinput` en `0666`. |
| `scripts/check-uinput.sh` | Diagnóstico de `/dev/uinput` en el host y, con `--contenedor`, dentro de `game-station`. `--solo-host` omite el contenedor. |

## uinput

Los gamepads virtuales usan `/dev/uinput`:

```bash
./scripts/check-uinput.sh                # diagnóstico en el host
sudo ./scripts/setup-uinput.sh           # Jetson/Tegra sin módulo uinput
./scripts/deploy.sh
./scripts/check-uinput.sh --contenedor   # verificación dentro del contenedor
```

En hosts Linux estándar suele bastar con `sudo modprobe uinput && sudo chmod 666 /dev/uinput`.

## HTTPS

Chrome exige un contexto seguro para WebRTC y la Gamepad API. El nginx del cliente sirve `443` con el
certificado que prepara [`docker/16-tls-certs.sh`](../game-client/docker/16-tls-certs.sh) al arrancar:

| `TLS_CERT` | Certificado |
| --- | --- |
| `self` (default) | Autofirmado con `CN=${PUBLIC_HOST}`. El navegador pide aceptar el riesgo la primera vez. |
| `acme` | Let's Encrypt vía [lego](https://go-acme.github.io/lego/), desafío HTTP-01 en el puerto `80`. Con una IP pública (sin dominio) solo existe el perfil *shortlived* de 6 días; se renueva solo a mitad de vida y nginx se recarga. |

Con `acme`, el puerto TCP `80` tiene que ser alcanzable desde internet. La cuenta y los certificados se
guardan en `./.acme` (volumen), así que sobreviven a recrear el contenedor.

## Red y firewall

- **REST y WebSocket** pasan por nginx: TCP `443` (HTTPS) y `5173` (HTTP). Con `TLS_CERT=acme`, también TCP `80`.
- **Video, audio e input** van por UDP directo al host de la estación (WebRTC, host network). Hay que abrir un **rango UDP amplio** (por ejemplo `10000-65535`) hacia el servidor.
- Si los navegadores están fuera de la LAN, `PUBLIC_HOST` e `ICE_HOST_IPS` tienen que ser la IP pública enrutable.
- Chrome no habilita WebRTC ni la Gamepad API en `http://<IP>`. Para jugar se usa `https://<PUBLIC_HOST>/` (con `TLS_CERT=self`, aceptando el certificado autofirmado).

## Operación

```bash
docker compose ps
docker compose logs -f --tail=80 game-station
docker compose logs -f --tail=80 game-client
curl -fsS http://127.0.0.1:8090/health
docker compose exec game-station tail -f /cache/<juego>.log   # salida del juego
```

Si se usa `compose.gpu.yaml`, hay que pasar los mismos `-f` que en `COMPOSE_FILES`.

## Solución de problemas

| Síntoma | Qué revisar |
| --- | --- |
| `deploy.sh`: `/dev/uinput no existe` | `./scripts/check-uinput.sh`. En Jetson: `sudo ./scripts/setup-uinput.sh`. |
| `deploy.sh`: `ICE_HOST_IPS sigue vacío` | No se detectó IP: editar `.env` o ejecutar `./scripts/configure-external.sh <IP>`. |
| Cliente: `Estación offline` | `GET /health` falla. Revisar `docker compose ps` y los logs de `game-station`. |
| Cliente: `sin video — ¿display en la estación? / ¿HTTPS?` | No llegó el track. Revisar que se use HTTPS y los logs `connectionstate=` / `ice=` de la estación. |
| ICE `failed` en LAN | `ICE_HOST_POLICY=public` sin `ICE_HOST_IPS` descarta IPs privadas. Usar `all` o definir `ICE_HOST_IPS` con la IP que ve el navegador. Revisar el firewall UDP. |
| Gamepad no responde | Acceder por HTTPS, presionar un botón del mando para que el navegador lo exponga y verificar `input_backend=` en los logs. |
| `PEER_BUSY` | Ya hay otro navegador conectado a la estación. |
| `STATION_BUSY` | La estación está en `PLAYING`. Hacer `stop` o esperar al idle timeout. |
