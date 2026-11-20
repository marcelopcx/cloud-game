#!/bin/sh
# BattleShip v1.6 — Super Smash Bros. 64 (GameWave / JRickey/BattleShip), linux/arm64.
set -eu

BS_TAG="${BATTLESHIP_TAG:-v1.6}"
ROM_PATH="${BATTLESHIP_ROM:-/build/baserom.us.z64}"

if [ ! -f "$ROM_PATH" ]; then
  echo "BattleShip: falta el ROM legal NTSC-U v1.0." >&2
  echo "Copiá tu baserom.us.z64 en games/battleship/baserom.us.z64 antes del docker build." >&2
  exit 1
fi

apt-get update
apt-get install -y --no-install-recommends \
  ninja-build clang curl \
  libsdl2-dev libzip-dev libglew-dev \
  nlohmann-json3-dev libtinyxml2-dev libspdlog-dev \
  libgl1-mesa-dev libegl1-mesa-dev \
  libxrandr-dev libxinerama-dev libxcursor-dev libxi-dev \
  python3 python3-pil imagemagick file git ca-certificates openssl fonts-dejavu-core
update-ca-certificates
export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
export GIT_SSL_CAINFO=/etc/ssl/certs/ca-certificates.crt
git config --global http.sslCAInfo /etc/ssl/certs/ca-certificates.crt

# Jammy trae CMake 3.22; BattleShip (libultraship) pide >= 3.24.
CMAKE_VERSION="${CMAKE_VERSION:-3.31.6}"
CMAKE_PREFIX="cmake-${CMAKE_VERSION}-linux-aarch64"
CMAKE_DIR="/opt/${CMAKE_PREFIX}"
if [ ! -x "${CMAKE_DIR}/bin/cmake" ]; then
  curl -fsSL "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/${CMAKE_PREFIX}.tar.gz" \
    | tar xz -C /opt
fi
export PATH="${CMAKE_DIR}/bin:${PATH}"
cmake --version
for stub in zipcmp zipmerge ziptool; do
  if [ ! -x "/usr/bin/$stub" ]; then
    printf '#!/bin/sh\nexit 0\n' > "/usr/bin/$stub"
    chmod +x "/usr/bin/$stub"
  fi
done

# --depth 1 + submódulos deja SHAs sin el commit fijado; git reintenta fetch y falla si SSL/CA no está bien.
git clone --branch "$BS_TAG" --recurse-submodules \
  https://github.com/JRickey/BattleShip.git /tmp/battleship
cp "$ROM_PATH" /tmp/battleship/baserom.us.z64

# Pillow 9+ no soporta textbbox() con load_default(); BattleShip v1.6 lo usa para nameplates.
python3 <<'PATCH'
from pathlib import Path

path = Path("/tmp/battleship/tools/derive_stage_assets.py")
text = path.read_text()
old = "    font = ImageFont.load_default()"
new = """    font = None
    for _ttf in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(_ttf).is_file():
            font = ImageFont.truetype(_ttf, 8)
            break
    if font is None:
        raise RuntimeError(
            "No TTF for nameplate rendering (install fonts-dejavu-core)"
        )"""
if old not in text:
    raise SystemExit("derive_stage_assets.py: anchor not found — BattleShip tag changed?")
path.write_text(text.replace(old, new, 1))
PATCH

cd /tmp/battleship
export CC=clang CXX=clang++
cmake -B build -DCMAKE_BUILD_TYPE=Release -DNON_PORTABLE=ON -DSSB64_VERSION=us -GNinja
cmake --build build -j"$(nproc)"

install -d /opt/battleship
# CMake deja el bundle completo al lado del binario (yamls/, f3d.o2r, fuentes, torch…).
cd build
for p in *; do
  case "$p" in
    CMakeFiles|Testing|*.ninja|build.ninja|cmake_install.cmake|*.a|*.cmake) continue ;;
  esac
  cp -a "$p" /opt/battleship/
done
if [ ! -x /opt/battleship/BattleShip ]; then
  echo "BattleShip: no se generó el ejecutable" >&2
  ls -la /opt/battleship >&2
  exit 1
fi
chmod +x /opt/battleship/BattleShip
install -m 755 /tmp/battleship-run-game.sh /opt/battleship/run-game.sh

rm -rf /tmp/battleship "${CMAKE_DIR}"

# .so de runtime (autoremove tras purgar -dev no debe quitarlas). No purgar curl: healthcheck Docker.
apt-get install -y --no-install-recommends \
  libsdl2-2.0 libzip4 libglew2.2 libspdlog1 libtinyxml2-9 libfmt8
apt-get purge -y ninja-build clang git python3-pil imagemagick \
  libsdl2-dev libzip-dev libglew-dev nlohmann-json3-dev libtinyxml2-dev libspdlog-dev \
  libgl1-mesa-dev libegl1-mesa-dev libxrandr-dev libxinerama-dev libxcursor-dev libxi-dev
apt-get autoremove -y --purge
rm -rf /var/lib/apt/lists/*
echo "BattleShip: instalado en /opt/battleship"
