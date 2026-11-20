# Game Station

Runtime que ejecuta un juego Linux en un display virtual, lo transmite al navegador por
WebRTC y recibe el input del jugador. Cada imagen Docker de la estación sirve **un solo juego**,
definido por su `manifest.yaml`.

| | |
| --- | --- |
| Código | [`game-station/`](../../game-station) |
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.4.5 (`web`, `websocket`, `validation`) |
| WebRTC | [`webrtc-java`](https://github.com/devopvoid/webrtc-java) 0.18.0 (libwebrtc nativo) |
| Nativo | JNA 5.16.0 (`/dev/uinput`, `libXtst`) |
| Manifiestos | Jackson `dataformat-yaml` |
| Puerto | `8090` (`STATION_HTTP_PORT`) |

Más detalle en:

- [API REST y WebSocket](api.md)
- [Video, audio y red](media.md)
- [Input remoto](input.md)

## Estructura

```
com.cloudgame.station
├── StationApplication        arranque Spring Boot
├── config
│   ├── StationProperties     propiedades station.* (application.yml + env)
│   └── StationConfig         CORS y registro de /ws/control y /ws/webrtc
├── controller
│   └── StationController     GET /health · POST /prepare · /launch · /stop
├── websocket
│   ├── ControlSocket         /ws/control: fan-out de STATE / ERROR
│   └── WebrtcSocket          /ws/webrtc: signaling OFFER / ANSWER / ICE
├── service
│   ├── StationService        máquina de estados, idle timeout, health
│   ├── ControlHub            broadcast JSON a los sockets de control
│   ├── FilePreparer          copia library → cache con verificación SHA-256
│   ├── GameCatalog           carga el manifiesto y arma el argv del juego
│   ├── GameRuntime           Xvfb, chequeo de Pulse, spawn del juego y de xfocus-game
│   ├── MediaSession          interfaz del peer (desacopla webrtc-java)
│   ├── WebrtcMediaSession    peer WebRTC, captura de video/audio, DataChannel
│   └── InputSink             aplica los datagramas de input (pads, XTEST, look)
├── infrastructure
│   ├── DatagramParser        parser del datagrama de input v3
│   ├── UinputPad             gamepad Xbox 360 virtual en /dev/uinput
│   ├── PadFeed               estado de pads en /tmp/game-input (libgameinput.so)
│   ├── LookFeed              deltas de mouse relativo en /tmp/game-input
│   ├── SdlMapping            SDL_GAMECONTROLLERCONFIG del pad virtual
│   ├── VirtualPadIds         VID/PID USB propios del pad virtual
│   ├── X11Injector           teclado y mouse vía XTEST
│   └── ProcessGroup          spawn y teardown de procesos de la partida
├── model
│   ├── GameManifest          manifest.yaml (command, env, needs, audio)
│   └── StationState          IDLE · PREPARING · READY · PLAYING · FAILED
├── dto                       PrepareRequest · SourceBody · LaunchRequest · StopRequest
└── exception                 StationException · PrepareException · StationExceptionHandler
```

## Ciclo de vida

| Estado | Entra por | Qué hace la estación |
| --- | --- | --- |
| `IDLE` | arranque, `stop`, idle timeout, 2 s después de `FAILED` | Nada en ejecución. |
| `PREPARING` | `POST /prepare` | Copia el payload a `CACHE_ROOT` en un hilo aparte (`station-fsm`) y publica el progreso por `/ws/control`. |
| `READY` | fin de la copia con checksum válido | Espera `launch`. |
| `PLAYING` | `POST /launch` | Levanta Xvfb, el juego, la captura y el input. Acepta un peer WebRTC. |
| `FAILED` | error en prepare | Emite `ERROR` y `STATE FAILED`, y a los 2 s ejecuta `stop`. |

Con `kind: test-pattern` (o un manifiesto sin `command`), `prepare` pasa directo a `READY`
sin tocar disco y `launch` no levanta display ni binario: el video son barras SMPTE generadas
en la JVM. Es el manifiesto embebido por defecto (`src/main/resources/manifest.yaml`).

## Configuración

Propiedades de `application.yml`, sobreescribibles por variables de entorno:

| Variable | Default (`application.yml`) | Default en imagen Docker | Uso |
| --- | --- | --- | --- |
| `STATION_HTTP_PORT` | `8090` | `8090` | Puerto HTTP / WS. |
| `STATION_ID` | `spark-1` | — (compose: `station-1`) | Identificador devuelto en `/health`. |
| `STATION_DISPLAY` | `:99` | `:99` | Display de Xvfb y de la captura. |
| `STATION_SIZE` | `1280x720` | `1280x720` | Resolución de Xvfb, de la captura y del mouse absoluto (`gameWidth`/`gameHeight`). |
| `STATION_STREAM_SIZE` | vacío | — | Resolución del video enviado. Vacío = `STATION_SIZE`. Se redondea a par y nunca supera `STATION_SIZE`. |
| `STATION_STREAM_SCALER` | `bicubic` | — | Filtro de `-vf scale` (`bicubic`, `lanczos`, `bilinear`, `area`, `fast_bilinear`). Otro valor vuelve a `bicubic`. |
| `STATION_FPS` | `60` | `60` | FPS de captura y `maxFramerate` del encoder. |
| `STATION_VIDEO_BITRATE` | `5000000` | — | `maxBitrate` del encoder, en bps. |
| `LIBRARY_ROOT` | `/opt/station-library` | `/opt/station-library` | Origen de los payloads de `prepare`. |
| `CACHE_ROOT` | `/tmp/cloudgame-cache` | `/cache` | Destino de la copia. En compose es el volumen `station-cache`. |
| `IDLE_TIMEOUT_S` | `300` | `300` | Segundos sin input en `PLAYING` antes de hacer `stop`. `0` lo desactiva. |
| `SAVES_URL` | vacío | lo pasa el dispatcher | game-crud para los saves de jugadores con sesión (`saveGrant` en `launch`). Vacío o sin `STATION_RESET_SAVES` = todos se tratan como anónimos. |
| `STATION_RESET_SAVES` | `false` | `true` | Al hacer `stop` después de una partida, borra los datos del jugador en el mismo contenedor: vacía `CACHE_ROOT` (menos los `*.log` de primer nivel), restaura `HOME` a como estaba al arrancar la estación y borra los `saves` del manifiesto. El siguiente `prepare` recarga el juego limpio. Apagado fuera de la imagen porque en desarrollo `HOME` es el del usuario. |
| `GAME_MANIFEST` | vacío (test-pattern embebido) | `/opt/game/manifest.yaml` | Manifiesto del juego. |
| `CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | — | Orígenes permitidos (REST y WS), separados por coma. Admite patrones. |
| `ICE_SERVERS` | `stun:stun.l.google.com:19302` | — | Servidores STUN/TURN, separados por coma. |
| `ICE_HOST_IPS` | vacío | — | Si se define, solo se anuncian candidatos `host` con esas IPs. |
| `ICE_HOST_POLICY` | `all` | — | `public` descarta candidatos `host` loopback, link-local y privados. |
| `PULSE_SERVER` | — | `unix:/tmp/pulse/native` | Socket del daemon Pulse. |
| `STATION_PULSE_SOURCE` | — | `game.monitor` | Fuente Pulse que se captura como audio. |

### Placeholders del manifiesto

`command`, `args`, `env` y `audio.fallbackArgs` admiten estos placeholders, que se reemplazan
al lanzar:

| Placeholder | Valor |
| --- | --- |
| `${WIDTH}` / `${HEIGHT}` | Ancho y alto de `STATION_SIZE`. |
| `${SIZE}` | `STATION_SIZE` completo. |
| `${DISPLAY}` | `STATION_DISPLAY`. |
| `${FPS}` | `STATION_FPS`. |
| `${CACHE}` | `CACHE_ROOT`. |
| `${LIBRARY}` | `LIBRARY_ROOT`. |

El esquema completo del manifiesto está en [Juegos](../games/README.md#manifiesto).

## Imagen Docker

[`game-station/Dockerfile`](../../game-station/Dockerfile) genera la imagen base
`cloudgame/game-station-runtime:latest`, que después extiende cada juego.

1. **Build**: `maven:3.9.9-eclipse-temurin-21` ejecuta `mvn package`. El jar se compila dentro de la imagen para que `webrtc-java` traiga el nativo de la arquitectura del host (por ejemplo `linux-aarch64`).
2. **Runtime**: `eclipse-temurin:21-jre-jammy` con `ffmpeg`, `xvfb`, Mesa/GL, librerías X11 (`libxtst6`, etc.), ALSA, OpenAL, PulseAudio y `curl`.
3. **Binarios C** compilados en la imagen desde [`game-station/docker/`](../../game-station/docker):

| Binario | Fuente | Función |
| --- | --- | --- |
| `/usr/local/lib/libgameinput.so` | `gameinput/gameinput.c` | Se precarga (`LD_PRELOAD`) en el juego. Inyecta pads virtuales y mouse relativo en SDL leyendo `/tmp/game-input`. |
| `/usr/local/bin/xfocus-game` | `x11/xfocus-game.c` | Cada 400 ms busca la ventana visible más grande, la ajusta al display y le da foco. |
| `/usr/local/bin/cloudgame-uinput-smoke` | `uinput-smoke.c` | Smoke test de `/dev/uinput`, que usa `scripts/check-uinput.sh`. |

4. **Scripts de la imagen**:
   - `station-entrypoint` ([`docker/entrypoint.sh`](../../game-station/docker/entrypoint.sh)): arranca PulseAudio con [`game.pa`](../../game-station/docker/pulse/game.pa) (null-sink `game`, socket `/tmp/pulse/native`) y luego la JVM con los `--add-opens` que requiere `webrtc-java`.
   - `station-seed-library` ([`docker/seed-library.sh`](../../game-station/docker/seed-library.sh)): crea `LIBRARY_ROOT/<gameId>/<version>/payload.bin` (32 MB de ceros) y su sidecar `checksum`. Lo invoca el Dockerfile de cada juego.
5. **Audio**: `asound.conf` y `alsoft.conf` redirigen ALSA y OpenAL a Pulse. `SDL_AUDIODRIVER=pulse`.

## Build y tests

```bash
cd game-station
mvn -B test                   # tests
mvn -B -DskipTests package    # target/game-station-1.0.0.jar
```

| Test | Cubre |
| --- | --- |
| `StationHttpTest` | Flujo HTTP completo con el manifiesto test-pattern: prepare → READY → launch → stop. |
| `DatagramParserTest` | Decodificación del datagrama de input que envía el navegador. |
| `PadFeedTest` | Layout binario de `/tmp/game-input` que lee `libgameinput.so`. |

El workflow [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) ejecuta `mvn -B test`
(Temurin 21) en cada push a `main` y en cada pull request.

## Logs

La estación loguea en formato `clave=valor` bajo `com.cloudgame.station` (nivel `INFO`). Algunas
líneas útiles:

| Línea | Significado |
| --- | --- |
| `state=PREPARING` / `state=READY` / `state=IDLE` | Transiciones de la máquina de estados. |
| `start_source game=… encoder=openh264\|smpte` | Arranque de la fuente de video. |
| `ice policy=… hostIps=… stun=…` | Configuración ICE efectiva al arrancar. |
| `connectionstate=…` / `ice=…` | Estado del peer WebRTC. |
| `video stats captureFps=… encodeFps=… limit=… rttMs=…` | Métricas del encoder cada 5 s. |
| `input_backend=uinput+padfeed\|sdl_preload` | Backend de gamepad elegido. |

La salida de cada juego se redirige a `CACHE_ROOT/<juego>.log`, desde su `run-game.sh`.
