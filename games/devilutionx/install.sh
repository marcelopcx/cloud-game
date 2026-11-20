#!/bin/sh
# DevilutionX 1.5.5 (linux/aarch64) + spawn.mpq (Diablo shareware).
set -eu

VERSION="${DEVX_VERSION:-1.5.5}"
case "$(uname -m)" in
  aarch64) ASSET=devilutionx-linux-aarch64.tar.xz ;;
  x86_64) ASSET=devilutionx-linux-x86_64.tar.xz ;;
  *) echo "DevilutionX: arquitectura $(uname -m) no soportada" >&2; exit 1 ;;
esac
SPAWN_SHA=64427cd7c1ba904eaa2e0031c16a6b136d0ecef9abc888c5ff8344b459356e38

apt-get update
apt-get install -y --no-install-recommends xz-utils \
  libsdl2-image-2.0-0 libsodium23 libbz2-1.0

curl -fL --retry 3 -o /tmp/devx.tar.xz \
  "https://github.com/diasurgical/devilutionX/releases/download/${VERSION}/${ASSET}"
mkdir -p /opt/devilutionx
tar -xJf /tmp/devx.tar.xz -C /opt/devilutionx
rm -f /tmp/devx.tar.xz
# El tar trae el binario en la raíz o dentro de una carpeta según la versión.
BIN=$(find /opt/devilutionx -type f -name devilutionx | head -1)
if [ -z "$BIN" ]; then
  echo "DevilutionX: no se encontró el binario en $ASSET" >&2
  exit 1
fi
if [ "$BIN" != /opt/devilutionx/devilutionx ]; then
  SRC_DIR=$(dirname "$BIN")
  cp -a "$SRC_DIR"/. /opt/devilutionx/
  rm -rf "$SRC_DIR"
fi
chmod +x /opt/devilutionx/devilutionx

curl -fL --retry 3 -o /opt/devilutionx/spawn.mpq \
  https://github.com/diasurgical/devilutionx-assets/releases/download/v5/spawn.mpq
echo "$SPAWN_SHA  /opt/devilutionx/spawn.mpq" | sha256sum -c -

apt-get purge -y xz-utils
apt-get autoremove -y --purge
rm -rf /var/lib/apt/lists/*
if ldd /opt/devilutionx/devilutionx | grep 'not found'; then
  echo "DevilutionX: faltan bibliotecas" >&2
  exit 1
fi
echo "DevilutionX: instalado en /opt/devilutionx"
