#!/bin/sh
# Toolchain común de los ports libultraship (HarbourMasters) sobre Ubuntu 22.04 (misma glibc que el runtime).
# Replica el CI upstream: SDL2 2.30, SDL2_net 2.2, tinyxml2 10 y libzip 1.10 desde fuente; CMake >= 3.26.
set -eu

apt-get update
apt-get install -y --no-install-recommends \
  build-essential clang git ca-certificates curl ninja-build lsb-release pkg-config file \
  python3 python3-pip zlib1g-dev libbz2-dev libpng-dev libglew-dev libgl-dev libegl-dev libgles-dev libopengl-dev \
  nlohmann-json3-dev libspdlog-dev libfmt-dev libboost-dev \
  libogg-dev libvorbis-dev libopus-dev libopusfile-dev \
  libx11-dev libxext-dev libxrandr-dev libxcursor-dev libxi-dev libxinerama-dev libxss-dev \
  libxxf86vm-dev libxkbcommon-dev libpulse-dev libasound2-dev libudev-dev libusb-1.0-0-dev libdbus-1-dev

CMAKE_VERSION="${CMAKE_VERSION:-3.31.6}"
curl -fsSL "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-linux-$(uname -m).tar.gz" \
  | tar xz -C /opt
ln -sf /opt/cmake-${CMAKE_VERSION}-linux-$(uname -m)/bin/* /usr/local/bin/

mkdir -p /tmp/deps && cd /tmp/deps
curl -fsSL https://github.com/libsdl-org/SDL/releases/download/release-2.30.3/SDL2-2.30.3.tar.gz | tar xz
(cd SDL2-2.30.3 && ./configure --enable-hidapi-libusb --disable-static && make -j"$(nproc)" && make install)
curl -fsSL https://github.com/libsdl-org/SDL_net/releases/download/release-2.2.0/SDL2_net-2.2.0.tar.gz | tar xz
(cd SDL2_net-2.2.0 && ./configure --disable-static && make -j"$(nproc)" && make install)
curl -fsSL https://github.com/leethomason/tinyxml2/archive/refs/tags/10.0.0.tar.gz | tar xz
cmake -S tinyxml2-10.0.0 -B tinyxml2-build -GNinja -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -Dtinyxml2_BUILD_TESTING=OFF
cmake --build tinyxml2-build && cmake --install tinyxml2-build
curl -fsSL https://github.com/nih-at/libzip/releases/download/v1.10.1/libzip-1.10.1.tar.gz | tar xz
cmake -S libzip-1.10.1 -B libzip-build -GNinja -DCMAKE_BUILD_TYPE=Release \
  -DENABLE_COMMONCRYPTO=OFF -DENABLE_GNUTLS=OFF -DENABLE_MBEDTLS=OFF -DENABLE_OPENSSL=OFF \
  -DBUILD_TOOLS=ON -DBUILD_REGRESS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_DOC=OFF
cmake --build libzip-build && cmake --install libzip-build
ldconfig
cd / && rm -rf /tmp/deps /var/lib/apt/lists/*
