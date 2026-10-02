#!/usr/bin/env bash
#
# Builds the psiphon-tunnel-core ConsoleClient for Android and installs it as
# app/src/main/jniLibs/<abi>/libpsiphon.so (git-ignored; the app execs it from
# nativeLibraryDir exactly like libtor.so).
#
# Why a process and not the library: the AAR's gomobile bindings share the
# go.Seq bridge with Tor's IPtProxy runtime, and two Go runtimes cannot live
# in one process. A standalone executable has its own process and its own
# runtime, so Tor stays untouched.
#
# Pinned to PSIPHON_VERSION so the binary always matches server_entries.txt.
# Go >= 1.26 with -checklinkname=0 is required (see anet README).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JNI_DIR="${PROJECT_DIR}/app/src/main/jniLibs"
WORK="${RUNNER_TEMP:-/tmp}/psiphon-cli"

PSIPHON_VERSION="v2.0.39"

mkdir -p "${WORK}" "${JNI_DIR}/arm64-v8a" "${JNI_DIR}/armeabi-v7a"

if [ ! -d "${WORK}/psiphon-tunnel-core" ]; then
  git clone --depth 1 --branch "${PSIPHON_VERSION}" \
    https://github.com/Psiphon-Labs/psiphon-tunnel-core "${WORK}/psiphon-tunnel-core"
fi

build_one() {
  local goos="$1" goarch="$2" goarm="$3" cc="$4" out="$5"
  echo "==> psiphon ConsoleClient ${goos}/${goarch}${goarm} -> ${out}"
  (
    cd "${WORK}/psiphon-tunnel-core"
    export GOFLAGS="-mod=vendor" GOOS="${goos}" GOARCH="${goarch}"
    if [ -n "${goarm}" ]; then export GOARM="${goarm}"; else unset GOARM; fi
    if [ -n "${cc}" ]; then export CC="${cc}" CGO_ENABLED=1; else export CGO_ENABLED=0; fi
    go build -trimpath -ldflags="-s -w -checklinkname=0" \
      -o "${out}" ./ConsoleClient
  )
  chmod +x "${out}"
}

build_one android arm64 "" "" "${JNI_DIR}/arm64-v8a/libpsiphon.so"

if [ -n "${ANDROID_NDK_HOME:-}" ]; then
  TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
  build_one android arm "7" \
    "${TOOLCHAIN}/bin/armv7a-linux-androideabi26-clang" \
    "${JNI_DIR}/armeabi-v7a/libpsiphon.so" || \
    echo "WARNING: armv7 psiphon build failed; psiphon stays arm64-only."
else
  echo "WARNING: ANDROID_NDK_HOME unset; skipping armv7 psiphon build."
fi

echo "==> installed:"
ls -la "${JNI_DIR}/arm64-v8a/" | grep -E "libpsiphon" || true
ls -la "${JNI_DIR}/armeabi-v7a/" | grep -E "libpsiphon" || true
