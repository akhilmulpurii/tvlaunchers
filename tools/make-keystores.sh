#!/bin/bash
# Creates a release keystore per launcher in keystores/ (git-ignored), with random passwords in
# keystores/keystore.properties. Skips any that exist. Back the folder up: a lost key can't sign updates.
cd "$(dirname "$0")/.." || exit 1
. tools/env.sh
mkdir -p keystores && cd keystores
P=keystore.properties
[ -f $P ] || echo "# Release signing. Back this folder up somewhere safe." > $P
for app in "${LAUNCHERS[@]}"; do
  [ -f "$app-release.p12" ] && { echo "$app: exists"; continue; }
  pw=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-28)
  keytool -genkeypair -storetype PKCS12 -keystore "$app-release.p12" -alias "$app" -keyalg RSA -keysize 4096 \
    -validity 10950 -storepass "$pw" -keypass "$pw" -dname "CN=Akhil Mulpuri, O=akhilmulpuri, C=US" || exit 1
  printf '%s.storeFile=%s-release.p12\n%s.alias=%s\n%s.password=%s\n' "$app" "$app" "$app" "$app" "$app" "$pw" >> $P
  echo "$app: created"
done
