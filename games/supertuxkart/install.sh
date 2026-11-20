#!/bin/sh
# Descarga el tarball oficial de SuperTuxKart (sin commitear binarios).
# Invocado desde el Dockerfile con TARGETARCH y STK_VERSION.
set -eu

VERSION="${STK_VERSION:?STK_VERSION requerido}"
ARCH="${TARGETARCH:?TARGETARCH requerido}"

case "$ARCH" in
  arm64 | aarch64)
    PKG=linux-arm64
    EXPECTED_BYTES=734267951
    SHA256=961b4b691b44547cdcada7ba39dad1561a6e7e766c90b22ca2df1fa18aa2e477
    ;;
  amd64 | x86_64)
    PKG=linux-x86_64
    EXPECTED_BYTES=735211939
    SHA256=57090b6c2163eb691f20104ae9712204acbb4e8341059dee0a5ff5315efc401b
    ;;
  *)
    echo "install: arquitectura no soportada: $ARCH (usá arm64 o amd64)" >&2
    exit 1
    ;;
esac

URL="https://github.com/supertuxkart/stk-code/releases/download/${VERSION}/SuperTuxKart-${VERSION}-${PKG}.tar.gz"
ARCHIVE=/tmp/stk.tar.gz
WORKDIR=/tmp/stk-extract

echo "install: SuperTuxKart ${VERSION} (${PKG})"
echo "install: ${URL}"
curl -fL --retry 3 --connect-timeout 30 -o "$ARCHIVE" "$URL"

ACTUAL_BYTES=$(wc -c <"$ARCHIVE" | tr -d ' ')
if [ "$ACTUAL_BYTES" != "$EXPECTED_BYTES" ]; then
  echo "install: tamaño inesperado (${ACTUAL_BYTES} != ${EXPECTED_BYTES}) — revisá STK_VERSION o checksums" >&2
  exit 1
fi
echo "${SHA256}  ${ARCHIVE}" | sha256sum -c -

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"
tar -xzf "$ARCHIVE" -C "$WORKDIR"

RUN_SCRIPT=$(find "$WORKDIR" -maxdepth 4 -name run_game.sh -print -quit)
if [ -z "$RUN_SCRIPT" ]; then
  echo "install: no se encontró run_game.sh en el archive" >&2
  exit 1
fi

STK_ROOT=$(dirname "$RUN_SCRIPT")
rm -rf /opt/stk
mkdir -p /opt/stk
cp -a "$STK_ROOT"/. /opt/stk/
chmod +x /opt/stk/run_game.sh
if [ -f /opt/stk/bin/supertuxkart ]; then
  chmod +x /opt/stk/bin/supertuxkart
fi

printf '%s %s\n' "$VERSION" "$PKG" >/opt/stk/.brand-build
rm -rf "$ARCHIVE" "$WORKDIR"
echo "install: listo en /opt/stk (${VERSION} ${PKG})"
