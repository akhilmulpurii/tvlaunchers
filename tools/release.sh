#!/bin/bash
# Builds signed release APKs of every launcher into dist/ (one universal APK each; no native code, so no per-ABI splits).
cd "$(dirname "$0")/.." || exit 1
set -o pipefail
. tools/env.sh
[ -f keystores/keystore.properties ] || { echo "keystores/keystore.properties missing: run tools/make-keystores.sh first"; exit 1; }
mkdir -p dist
for p in "${LAUNCHERS[@]}"; do
  echo "== $p"
  tools/build.sh "$p" clean assembleRelease | tail -2 || exit 1
  v=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" "$p/app/build.gradle")
  cp "$p/app/build/outputs/apk/release/app-release.apk" "dist/$p-$v-universal.apk" || exit 1
done
(cd dist && shasum -a 256 *.apk > SHA256SUMS && cat SHA256SUMS)
