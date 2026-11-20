#!/usr/bin/env bash
# Compila e instala el módulo uinput en hosts Linux sin CONFIG_INPUT_UINPUT
# (p. ej. Jetson 6.8.12-tegra). Requiere root.
#
#   sudo ./scripts/setup-uinput.sh
#
# Tras esto: /dev/uinput, carga al boot, permisos 0666.
set -euo pipefail

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  exec sudo "$0" "$@"
fi

KVER="$(uname -r)"
KBUILD="/lib/modules/${KVER}/build"
EXTRA="/lib/modules/${KVER}/extra"

if [[ ! -d "$KBUILD" ]]; then
  echo "error: no hay headers en $KBUILD" >&2
  echo "       probá: sudo apt install -y linux-headers-${KVER}" >&2
  exit 1
fi

if [[ -e /dev/uinput ]] && lsmod | grep -qE '^uinput\b'; then
  chmod 666 /dev/uinput 2>/dev/null || true
  echo "uinput ya activo: $(ls -l /dev/uinput)"
  exit 0
fi

if modprobe uinput 2>/dev/null && [[ -e /dev/uinput ]]; then
  chmod 666 /dev/uinput
  echo "uinput (kernel): $(ls -l /dev/uinput)"
  exit 0
fi

if ! command -v make >/dev/null || ! command -v curl >/dev/null; then
  apt-get update
  apt-get install -y --no-install-recommends build-essential curl ca-certificates
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

KERNEL_BRANCH="v6.8"
BASE="https://raw.githubusercontent.com/torvalds/linux/${KERNEL_BRANCH}/drivers/input"
curl -fsSL "${BASE}/misc/uinput.c" -o "${WORKDIR}/uinput.c"
curl -fsSL "${BASE}/input-compat.h" -o "${WORKDIR}/input-compat.h"
sed -i 's|#include "../input-compat.h"|#include "input-compat.h"|' "${WORKDIR}/uinput.c"
echo 'obj-m += uinput.o' > "${WORKDIR}/Makefile"

echo "compilando uinput.ko para ${KVER}…"
make -C "$KBUILD" M="$WORKDIR" modules

mkdir -p "$EXTRA"
install -m 644 "${WORKDIR}/uinput.ko" "${EXTRA}/uinput.ko"
depmod -a

if ! modprobe uinput 2>/dev/null; then
  insmod "${EXTRA}/uinput.ko"
fi

if [[ ! -e /dev/uinput ]]; then
  echo "error: el módulo cargó pero /dev/uinput no existe" >&2
  exit 1
fi

chmod 666 /dev/uinput

printf '%s\n' uinput > /etc/modules-load.d/uinput.conf

cat > /etc/udev/rules.d/99-uinput.rules <<'EOF'
KERNEL=="uinput", MODE="0666", OPTIONS+="static_node=uinput"
EOF
if command -v udevadm >/dev/null; then
  udevadm control --reload-rules
  udevadm trigger --subsystem-match=misc --action=add 2>/dev/null || true
fi

echo "listo: $(ls -l /dev/uinput)"
echo "reiniciá game-station: ./scripts/deploy.sh"
