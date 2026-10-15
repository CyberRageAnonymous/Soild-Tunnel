#!/usr/bin/env bash
set -euo pipefail
ROOT="$PWD"
VERSION="${1:-2.0.41}"
OUT="$ROOT/app/libs/psiphontunnel-${VERSION}-nogo.aar"
if [ -f "$OUT" ]; then
    echo "repack-psiphon: $OUT already exists, skipping."
    exit 0
fi
SRC_URL="https://raw.githubusercontent.com/Psiphon-Labs/psiphon-tunnel-core-Android-library/master/ca/psiphon/psiphontunnel/${VERSION}/psiphontunnel-${VERSION}.aar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
echo "repack-psiphon: downloading $SRC_URL"
curl -sSL --retry 3 --max-time 300 -o "$WORK/orig.aar" "$SRC_URL"
mkdir -p "$WORK/aar" "$WORK/jar" "$ROOT/app/libs"
unzip -o -q "$WORK/orig.aar" -d "$WORK/aar"
unzip -o -q "$WORK/aar/classes.jar" -d "$WORK/jar"
rm -rf "$WORK/jar/go" "$WORK/aar/classes.jar"
(cd "$WORK/jar" && zip -q -r "$WORK/aar/classes.jar" .)
for abi in "$WORK"/aar/jni/*; do
    [ -f "$abi/libgojni.so" ] && mv "$abi/libgojni.so" "$abi/libpsiphongo.so"
done
(cd "$WORK/aar" && zip -q -r "$OUT" .)
echo "repack-psiphon: wrote $OUT"
echo "repack-psiphon: verifying contents"
unzip -l "$OUT" > "$WORK/aar.list"
grep -q "libpsiphongo.so" "$WORK/aar.list" || { echo "repack-psiphon: renamed .so missing"; exit 1; }
if grep -q "libgojni.so" "$WORK/aar.list"; then
    echo "repack-psiphon: old .so name still present"; exit 1
fi
unzip -p "$OUT" classes.jar > "$WORK/check.jar"
unzip -l "$WORK/check.jar" > "$WORK/jar.list"
if grep -q " go/" "$WORK/jar.list"; then
    echo "repack-psiphon: go/* classes still present"; exit 1
fi
for need in "psi/Psi.class" "ca/psiphon/PsiphonTunnel.class"; do
    grep -q "$need" "$WORK/jar.list" || { echo "repack-psiphon: $need missing"; exit 1; }
done
echo "repack-psiphon: OK"
