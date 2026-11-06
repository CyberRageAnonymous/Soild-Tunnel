#!/usr/bin/env bash
#
# Fetches what the native build needs:
#   1. hev-socks5-tunnel  -> built with its own Android.mk into libhev-socks5-tunnel.so
#                            (the in-app "tun2socks" core)
#   2. SoildTunnel engine src  -> cross-compiled into libsoildtunnel-core.so
#   3. the tor daemon      -> libtor.so unpacked from the official tor-android
#                            AAR on Maven Central (the one prebuilt core here;
#                            cross-compiling tor for Android is not our job)
#
# Items 1 and 2 are compiled later by scripts/build-natives.sh.
#
# Safe to re-run. All network access happens here / in CI, never on device.
# By default we clone hev's DEFAULT branch. To pin, export HEV_REF; a missing
# ref falls back to the default branch instead of failing.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
NATIVE_DIR="${PROJECT_DIR}/.native"
mkdir -p "${NATIVE_DIR}"

HEV_REPO="heiher/hev-socks5-tunnel"
HEV_REF="${HEV_REF:-}"            # empty => default branch
HEV_DIR="${NATIVE_DIR}/hev-socks5-tunnel"

SOILDTUNNEL_SRC="${NATIVE_DIR}/soildtunnel"

# The engine source is VENDORED inside this repo at native/engine so the app's
# own modifications are ALWAYS compiled into libsoildtunnel-core.so with zero
# manual steps and zero network access for the engine.
VENDORED_SOILDTUNNEL="${PROJECT_DIR}/native/engine"

# clone_repo <url> <dir> <ref>
# Tries the pinned ref first (tag or branch); on any failure cleanly falls back
# to the repo's default branch. Always clones submodules recursively.
clone_repo() {
  local url="$1" dir="$2" ref="$3"
  rm -rf "${dir}"
  if [ -n "${ref}" ] && \
     git clone --depth 1 --branch "${ref}" --recursive "${url}" "${dir}" 2>/dev/null; then
    echo "   cloned ${url} @ ${ref}"
    return 0
  fi
  if [ -n "${ref}" ]; then
    echo "   ref '${ref}' not found on ${url}; using default branch"
  fi
  rm -rf "${dir}"
  git clone --depth 1 --recursive "${url}" "${dir}"
  echo "   cloned ${url} @ default branch"
}

echo "==> Fetching hev-socks5-tunnel (tunnel core)"
clone_repo "https://github.com/${HEV_REPO}.git" "${HEV_DIR}" "${HEV_REF}"
if [ ! -f "${HEV_DIR}/Makefile" ]; then
  echo "ERROR: hev-socks5-tunnel checkout has no Makefile at ${HEV_DIR}" >&2
  ls -la "${HEV_DIR}" >&2 || true
  exit 1
fi

echo "==> Providing SoildTunnel engine source"
if ! find "${VENDORED_SOILDTUNNEL}/soildtunnel" -name Cargo.toml -not -path '*/target/*' 2>/dev/null | grep -q .; then
  echo "ERROR: the vendored engine at ${VENDORED_SOILDTUNNEL} has no Cargo.toml." >&2
  exit 1
fi
echo "   using the VENDORED engine bundled in this repo: ${VENDORED_SOILDTUNNEL}"
rm -rf "${SOILDTUNNEL_SRC}"
mkdir -p "${SOILDTUNNEL_SRC}"
# Copy everything except any local build output (target/).
( cd "${VENDORED_SOILDTUNNEL}" && tar --exclude='./target' --exclude='*/target' -cf - . ) \
  | ( cd "${SOILDTUNNEL_SRC}" && tar -xf - )

echo "   found SoildTunnel Cargo manifest(s):"
find "${SOILDTUNNEL_SRC}" -maxdepth 2 -name Cargo.toml -not -path '*/target/*' | sed 's/^/     /'

# --- tor daemon ------------------------------------------------------------------
# Guardian Project publishes the daemon as tor-android, but the app never uses
# a class from it: TorManager exec()s libtor.so straight out of
# nativeLibraryDir. It also cannot stay a Gradle dependency -- since 0.4.9.6
# the AAR metadata requires compileSdk 37 and this app builds against 35, and
# 0.4.9.14 (consensus protocol fixes + five security releases) is no
# exception -- so the binaries are taken from the published AAR instead.
TOR_VERSION="${TOR_VERSION:-0.4.9.14}"
TOR_AAR_URL="https://repo1.maven.org/maven2/info/guardianproject/tor-android/${TOR_VERSION}/tor-android-${TOR_VERSION}.aar"
TOR_AAR="${NATIVE_DIR}/tor-android-${TOR_VERSION}.aar"
JNI_LIBS="${PROJECT_DIR}/app/src/main/jniLibs"

echo "==> Fetching tor ${TOR_VERSION} daemon (libtor.so)"
for attempt in 1 2 3; do
  if curl -fsSL --max-time 300 -o "${TOR_AAR}.part" "${TOR_AAR_URL}"; then
    mv "${TOR_AAR}.part" "${TOR_AAR}"
    break
  fi
  rm -f "${TOR_AAR}.part"
  if [ "${attempt}" -eq 3 ]; then
    echo "ERROR: could not download ${TOR_AAR_URL}" >&2
    exit 1
  fi
  echo "   download failed (attempt ${attempt}/3), retrying in 5s" >&2
  sleep 5
done
if [ ! -s "${TOR_AAR}" ]; then
  echo "ERROR: downloaded tor AAR is empty at ${TOR_AAR}" >&2
  exit 1
fi

rm -rf "${NATIVE_DIR}/tor-aar"
mkdir -p "${NATIVE_DIR}/tor-aar"
unzip -q -o "${TOR_AAR}" "jni/*" -d "${NATIVE_DIR}/tor-aar"

# Same ABI list as build-natives.sh -- x86/x86_64 builds inside the AAR are
# never shipped, so they are deliberately not unpacked.
for abi in arm64-v8a armeabi-v7a; do
  src="${NATIVE_DIR}/tor-aar/jni/${abi}/libtor.so"
  if [ ! -s "${src}" ]; then
    echo "ERROR: tor ${TOR_VERSION} AAR has no libtor.so for ${abi}" >&2
    exit 1
  fi
  mkdir -p "${JNI_LIBS}/${abi}"
  cp "${src}" "${JNI_LIBS}/${abi}/libtor.so"
  echo "   jniLibs/${abi}/libtor.so ($(wc -c < "${src}" | tr -d ' ') bytes)"
done

echo "==> Native sources ready under ${NATIVE_DIR}"
