#!/usr/bin/env bash
# Builds the ocgcore shared library for the host platform and copies it into
# the engine's resources at natives/<platform>/, where NativeLoader finds it.
#
# Requires: meson, ninja, a C++17 compiler. On Windows use build.ps1 instead.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/native/ocgcore"
BUILD="$ROOT/native/build"

if [ ! -f "$SRC/ocgapi.h" ]; then
	echo "ocgcore submodule missing; run: git submodule update --init --recursive" >&2
	exit 1
fi

case "$(uname -s)" in
	Linux*)  OS=linux ;;
	Darwin*) OS=macos ;;
	MINGW*|MSYS*|CYGWIN*) echo "on Windows use native/build.ps1" >&2; exit 1 ;;
	*) echo "unsupported OS $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
	x86_64|amd64) ARCH=x86_64 ;;
	aarch64|arm64) ARCH=aarch64 ;;
	*) echo "unsupported arch $(uname -m)" >&2; exit 1 ;;
esac

MESON_ARGS=(--buildtype=release -Ddefault_library=shared)
case "$OS" in
	linux)
		# Static C++ runtime so players don't need a matching libstdc++.
		MESON_ARGS+=("-Dcpp_link_args=['-static-libstdc++','-static-libgcc']")
		PLATFORM="linux-$ARCH"; LIB=libocgcore.so ;;
	macos)
		# One universal binary covers Intel and Apple Silicon.
		ARCHS="['-arch','arm64','-arch','x86_64']"
		MESON_ARGS+=("-Dc_args=$ARCHS" "-Dcpp_args=$ARCHS" "-Dc_link_args=$ARCHS" "-Dcpp_link_args=$ARCHS")
		export MACOSX_DEPLOYMENT_TARGET=11.0
		PLATFORM="macos"; LIB=libocgcore.dylib ;;
esac

[ -d "$BUILD" ] && MESON_ARGS+=(--wipe)
meson setup "$BUILD" "$SRC" "${MESON_ARGS[@]}"
ninja -C "$BUILD"

OUT="$ROOT/engine/src/main/resources/natives/$PLATFORM"
mkdir -p "$OUT"
cp "$BUILD/$LIB" "$OUT/$LIB"
echo "Built $OUT/$LIB"
