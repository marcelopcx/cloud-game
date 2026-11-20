# Catálogo de juegos

Cada carpeta es una imagen Docker construida sobre `cloudgame/game-station-runtime`. La estación
sirve un solo juego por contenedor; se elige con `GAME=<carpeta>`.

```bash
./scripts/build-station.sh neverball          # solo build
./scripts/deploy-loader.sh neverball          # modo loader: reconstruye ese juego y lo deja disponible
GAME=neverball ./scripts/deploy.sh            # modo estación fija
```

Para que aparezca en el cliente, el juego tiene que estar en el catálogo (game-crud) con
`path` = `/games/<gameId>`: se agrega desde game-admin (`/admin/`).

### Un contenedor por juego (todos a la vez)

```bash
./scripts/deploy-all.sh                      # build + levanta todos los juegos de games/
./scripts/deploy-all.sh supertux neverball   # solo algunos
./scripts/deploy-all.sh --no-build           # reusa imágenes ya construidas
./scripts/deploy.sh                          # volver al modo de un solo juego
```

Genera `compose.multi.yaml` y `.multi/stations.conf` (no versionados). El juego *i* en orden
alfabético usa HTTP `8100+i` y `DISPLAY=:(100+i)`; el cliente habla con `/stations/<gameId>/`
y nginx lo manda a esa estación. Las estaciones no montan `/dev/input` del host: cada una crea
solo los nodos de sus pads uinput, así dos partidas simultáneas no se leen los mandos entre sí.

## Juegos

| `GAME` | Juego | Género | Pad | Teclado | Mouse | Origen |
| --- | --- | --- | --- | --- | --- | --- |
| `supertuxkart` | SuperTuxKart 1.5 | Carreras 3D | 2 | ✓ | ✓ | binario upstream |
| `battleship` | BattleShip v1.6 (SSB64) | Pelea | 4 | ✓ | ✓ | build + ROM propia |
| `supertux` | SuperTux 0.6.3 | Plataformas 2D | 1 | ✓ | ✓ | apt jammy |
| `extremetuxracer` | Extreme Tux Racer 0.8.1 | Carreras 3D | 1 | ✓ | ✓ | apt jammy |
| `neverball` | Neverball 1.6.0 | Puzzle 3D | 1 | ✓ | ✓ | apt jammy |
| `chromium-bsu` | Chromium B.S.U. 0.9.16 | Shoot 'em up | 1 | ✓ | ✓ | apt jammy |
| `astromenace` | AstroMenace 1.3.2 | Shoot 'em up 3D | 1 | ✓ | ✓ | apt jammy |
| `armagetronad` | Armagetron Advanced 0.2.9 | Arcade (Tron) | – | ✓ | – | apt jammy |
| `openttd` | OpenTTD 12.1 + OpenGFX/OpenSFX | Estrategia | – | ✓ | ✓ | apt jammy |
| `openarena` | OpenArena 0.8.8 | FPS (bots) | – | ✓ | relativo | apt jammy |
| `sauerbraten` | Cube 2: Sauerbraten | FPS (bots) | – | ✓ | relativo | apt jammy |
| `freedoom` | Freedoom Phase 2 en PrBoom+ | FPS clásico | – | ✓ | relativo | apt jammy |
| `devilutionx` | DevilutionX 1.5.5 (Diablo, shareware) | ARPG | 1 | ✓ | ✓ | binario upstream + `spawn.mpq` |
| `zelda3` | zelda3 (A Link to the Past) | Aventura 2D | 1 | ✓ | – | build + ROM propia |
| `smw` | smw (Super Mario World) | Plataformas 2D | 2 | ✓ | – | build + ROM propia |
| `sm` | sm (Super Metroid) | Metroidvania | 1 | ✓ | – | build + ROM propia |
| `soh` | Ship of Harkinian 9.2.3 (Zelda: Ocarina of Time) | Aventura 3D | 1 | ✓ | ✓ | build + ROM propia |
| `2ship` | 2 Ship 2 Harkinian 5.0.1 (Zelda: Majora's Mask) | Aventura 3D | 1 | ✓ | ✓ | build + ROM propia |
| `starship` | Starship 2.0.0 (Star Fox 64) | Shooter 3D | 1 | ✓ | ✓ | build + ROM propia |
| `spaghettikart` | SpaghettiKart 1.0.0 (Mario Kart 64) | Carreras 3D | 4 | ✓ | ✓ | build + ROM propia |
| `ghostship` | Ghostship 3.0.0 (Super Mario 64) | Plataformas 3D | 1 | ✓ | ✓ | build + ROM propia |
| `dusklight` | Dusklight 2.0.2 (Zelda: Twilight Princess) | Aventura 3D | 1 | ✓ | ✓ | binario upstream + disco propio · **requiere GPU (Vulkan)** |
| `bloodyroar2` | Bloody Roar II Recompiled 0.7.1 (PS1) | Pelea 3D | 2 | ✓ | – | build + disco propio |
| `sparks-and-paths` | Sparks and Paths (Spaceship Games 🇻🇪) | Puzzle | 1 | ✓ | ✓ | Godot 4.2.1 + build propio de itch.io |
| `cursed-ruby` | Cursed Ruby (MiracleJester 🇻🇪) | Plataformas 2D | 1 | ✓ | ✓ | Godot 3.6 + build propio de itch.io |
| `iwariwe` | Iwariwë: El dueño del fuego (Jarcelo 🇻🇪) | Aventura 2D | – | ✓ | ✓ | Godot 2.1.6 compilado + build propio de itch.io |
| `everydayness` | EVERYDAYNESS;; (nouzivr 🇻🇪) | Novela visual | 1 | ✓ | ✓ | Ren'Py (SDK ARM) + build propio de itch.io |

Los juegos de la primera tanda son software libre con datos libres. Los ports decompilados
(ver abajo) necesitan una copia propia del juego original. Las columnas
reflejan `needs` del `manifest.yaml`: el cliente habilita el input según esos flags.

## Estructura de un juego

```
games/<id>/
├── Dockerfile      FROM runtime + instalación (apt o install.sh)
├── manifest.yaml   id, command, env, needs, audio.fallbackArgs
└── run-game.sh     lee STATION_SIZE, escribe config inicial y hace exec del juego
```

Reglas que siguen todos:

- **Ventana, no fullscreen.** Xvfb no tiene WM; `xfocus-game` (en la estación) mueve la ventana
  más grande a `0,0`, la estira al tamaño del display y le da foco. Los juegos que no reescalan
  al cambiar de tamaño reciben el tamaño de `STATION_SIZE` por flag o config.
- **Log en `/cache/<id>.log`.** `run-game.sh` redirige stdout/stderr ahí.
- **`SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS=1`** en el manifiesto: sin WM, SDL cree que la ventana
  no tiene foco y descarta el joystick.
- **`audio.fallbackArgs`**: flags para desactivar el sonido si Pulse no arranca.
- **`station-seed-library <id> 1.0.0`** en el Dockerfile, para que `/prepare` encuentre el payload.

## Notas por juego

- **OpenTTD:** la config inicial pone `video_hw_accel = false`; si no, muestra un aviso porque
  Xvfb no tiene GPU. Se juega entero con mouse.
- **Armagetron Advanced:** la primera vez pide idioma (Enter). Solo teclado.
- **OpenArena / Sauerbraten / Freedoom:** usan `relativeMouse` (pointer lock en el browser).
- **Pad:** solo se declara en juegos SDL con soporte de joystick conocido. Validado en la
  Jetson con SuperTuxKart; en el resto falta probar el mando real sobre uinput.

## Ports decompilados (ROM propia)

Código C/C++ nativo obtenido de decompilaciones, compilado para arm64 dentro del `docker build`.
El repo **no** trae ROMs ni assets: cada uno copia la suya en la carpeta del juego antes del build
(los ignora el `.gitignore` de cada carpeta). Sin el archivo el build falla en el `COPY` del ROM; `deploy-all.sh` lo
omite y sigue con el resto.

| `GAME` | Archivo en `games/<id>/` | Versión aceptada |
| --- | --- | --- |
| `zelda3` | `zelda3.sfc` | USA (SHA1 `6D4F10A8…`) |
| `smw` | `smw.sfc` | USA, sin header (SHA1 `6B47BB75…`) |
| `sm` | `sm.smc` | JU, sin header (SHA1 `da957f0d…`) |
| `soh` | `baserom.z64` | Cualquiera de `supportedHashes.json` de Shipwright (NTSC 1.0/1.1/1.2, PAL, GC, MQ) |
| `2ship` | `baserom.z64` | Majora's Mask US N64 o JP GC |
| `starship` | `baserom.z64` | Star Fox 64 US 1.0 o 1.1 |
| `spaghettikart` | `baserom.z64` | Mario Kart 64 US (SHA1 `579C48E2…`) |
| `ghostship` | `baserom.z64` | Super Mario 64 US o JP |
| `devilutionx` | *(opcional)* `DIABDAT.MPQ`, `hellfire.mpq` | CD o GOG; sin ellos arranca el shareware |
| `dusklight` | un archivo `*.iso`/`*.rvz`/`*.gcm`/`*.ciso`/`*.gcz`/`*.wia`/`*.wbfs`/`*.nfs`/`*.tgc` (cualquier nombre) | Twilight Princess GameCube USA o PAL; discos Wii salvo el coreano |
| `bloodyroar2` | `Bloody Roar II (USA).bin` + `.cue` | USA SCUS-94424, Redump (MD5 Track 01 `fa5b8e19…`) |

Las ROMs de N64 van en formato **.z64** (big-endian). Si la tuya es `.n64`/`.v64`, convertila antes
(p. ej. con `hack64.net/tools/swapper.php`). Verificá el hash con `shasum <archivo>`.

```bash
cp ~/roms/zelda-oot.z64 games/soh/baserom.z64
./scripts/build-station.sh soh            # 20-40 min la primera vez en la Jetson
GAME=soh ./scripts/deploy.sh
```

Notas:

- **libultraship** (`soh`, `2ship`, `starship`, `spaghettikart`, `ghostship`): mismo esquema que
  BattleShip. La imagen tiene tres stages: toolchain (SDL2 2.30, tinyxml2 10, libzip 1.10 desde
  fuente, igual que el CI upstream), compilación del port y extracción de assets (`ExtractAssets`
  → `*.o2r`). El ROM entra recién en el último stage, así que cambiarlo no recompila el binario.
  Config y saves en `SHIP_HOME=/cache/<id>-ship`; `run-game.sh` escribe la config inicial con el
  tamaño de la ventana. Van en 16:9 (1280x720): los cinco soportan panorámico nativo. El menú de mejoras se abre con **F1** (o Esc según el port).
- **snesrev** (`zelda3`, `smw`, `sm`): C puro + SDL2, muy livianos. `zelda3` usa 16:9 panorámico si
  `STATION_SIZE` es 16:9; `smw` y `sm` van a 4:3 (`ENV STATION_SIZE=960x720` en el Dockerfile,
  como BattleShip; la estación no lee `size` del manifiesto). `sm` necesita el ROM también en
  ejecución (corre la versión emulada en paralelo para verificar).
- **DevilutionX**: binario oficial linux-aarch64 + `spawn.mpq` (shareware redistribuible, verificado
  por SHA256). Si agregás `DIABDAT.MPQ` en la carpeta, la imagen trae el juego completo.
- **Dusklight** (Twilight Princess): binario oficial `linux-arm64` (AppImage, SHA256 verificado),
  port nativo de la decompilación [zeldaret/tp](https://github.com/zeldaret/tp). Renderiza con
  **Vulkan** (Dawn/WebGPU); el build de Linux no trae OpenGL, así que necesita la GPU
  (`LOADER_GPU=true` o `compose.gpu.yaml`). El AppImage pide glibc 2.39: la imagen copia el glibc y
  libstdc++ de Ubuntu 24.04 a `/opt/dusklight/noble` y parchea el intérprete del binario con
  `patchelf`, sin tocar el resto del contenedor (jammy). Arranca directo al juego (`--dvd`), a 60 FPS.
  Config y saves en `/cache/dusklight-home` (se pierden al borrar el contenedor, como en los demás).
- **Bloody Roar II** ([novapowers0/BloodyRoar2Recomp](https://github.com/novapowers0/BloodyRoar2Recomp),
  framework psxrecomp): recompilación estática del ejecutable de PS1 a C. El release upstream es
  solo x64, así que el build genera el C desde el disco (`psxrecomp_cli.py generate`, valida el MD5)
  y compila el target `psx-runtime-us` (~1 min en la Jetson). El juego lee el CD en ejecución, por lo
  que el disco queda dentro de la imagen (~1,3 GB). OpenGL (Vulkan se compila como stub: jammy no
  tiene `glslc`), SDL3 estático, BIOS OpenBIOS incluida. `run-game.sh` escribe `settings.toml`
  (fullscreen borderless, sin launcher) y `keybinds.ini` junto al binario: P1 y P2 toman cada uno un
  mando uinput (`auto`) y el teclado maneja solo a P1 (flechas, X/S/Z/A, Q/W/E/R, Enter = Start).
  Probado en la Jetson: el mando 1 navega el menú y el mando 2 entra como 2P en la selección de personaje.
  Los mods del launcher (widescreen, saltar intro, desbloquear todo) quedan apagados.
- Descartados: CSE2 (Cave Story, retirado por DMCA), OpenGOAL/Jak (solo x86_64), recomps N64 con
  RT64 (requieren Vulkan; la estación renderiza con Mesa por software).

## Juegos venezolanos (catálogo de venezuelajuega.com)

Indies venezolanos gratuitos de [venezuelajuega.com](https://venezuelajuega.com/) que corren en la
Jetson (arm64) sin emulación x86: los hechos con motores cuyo paquete de datos es independiente de la
plataforma (Godot, Ren'Py), ejecutados con un runtime arm64 de la misma versión del motor.

El repo **no** trae los juegos: son de sus autores. Como con las ROMs, cada uno se baja de itch.io y se
deja en la carpeta del juego antes del build (lo ignora su `.gitignore`). Antes de ofrecerlos al
público hay que tener el permiso de cada autor para transmitirlos.

| `GAME` | Archivo en `games/<id>/` | De dónde |
| --- | --- | --- |
| `sparks-and-paths` | `SnP-WINDOWS.zip` (o `SparksandPaths.pck`) | https://dewott2501.itch.io/sparks-and-paths |
| `cursed-ruby` | `CursedRuby.exe` | https://miraclejester.itch.io/cursed-ruby |
| `iwariwe` | `Iwariwe_2019_64.tar.gz` (Linux 64) | https://jarcelo.itch.io/iwariwe-el-dueo-del-fuego |
| `everydayness` | el `.zip` del build **PC** | https://nouzivr.itch.io/everydayness ("paga lo que quieras", acepta 0) |

Notas:

- **Estado (2026-10-02):** los cuatro construyen en la Jetson y, levantados por el loader, llegan a
  `PLAYING` renderizando en la GPU Thor con audio por Pulse y el pad uinput abierto por el juego
  (en Cursed Ruby se verificó navegar el menú con el D-pad). Dados de alta en el catálogo y con sus
  controles en `seed_game_inputs.sql`. Falta una partida completa desde el navegador.
- **Godot:** se usa el ejecutable de Windows/Linux solo como contenedor del `.pck` (`--main-pack`
  abre también el pack embebido al final del `.exe`). Sparks and Paths usa el renderer
  `gl_compatibility` (OpenGL 3.3), así que no depende de Vulkan. Cursed Ruby (3.4.3) corre sobre el
  binario oficial arm64 de 3.6, el primero de la rama 3.x con build arm64. Iwariwë es Godot 2.1, sin
  build arm64 oficial: la imagen compila el template release de 2.1.6 con gcc 9 y **sin udev** (con
  udev solo acepta pads marcados `ID_INPUT_JOYSTICK` por la base de udev, que el contenedor no tiene).
  Aun así el juego no usa mando: su InputMap (`engine.cfb`) y sus scripts solo tienen teclado, por
  eso el manifiesto declara `gamepad: 0`.
- **Ren'Py:** el zip PC trae Python solo para x86_64; el build detecta la versión de Ren'Py del juego
  y le agrega el `lib/py*-linux-aarch64` del SDK ARM oficial de esa versión (EVERYDAYNESS 1.0.1 usa
  Ren'Py 8.5.2). El lanzador del SDK se copia con el nombre del juego porque busca `<nombre>.py`.
- Descartados por ahora: los Unity/Unreal/GameMaker con build de Linux (Underhero, Sokobond Express,
  VA-11 Hall-A…) son x86_64 y de pago; los PICO-8 (Cosmos, Palmix Wave) traen el runtime x86_64;
  los web (Unity WebGL, Phaser, HaxeFlixel, Three.js) y los Flash necesitarían un navegador o Ruffle
  dentro de la estación.
