#!/bin/bash
# Usage: tools/build.sh <launcher> [gradle tasks...]   (default task: assembleDebug)
# Also accepts --project <launcher>, as queued by the relay.
cd "$(dirname "$0")/.." || exit 1
. tools/env.sh
[ "$1" = --project ] && shift
p=${1#../}; p=${p%/}
[ -n "$p" ] && [ -f "$p/settings.gradle" ] || { echo "usage: tools/build.sh <${LAUNCHERS[*]// /|}> [tasks]"; exit 1; }
shift
cd "$p" || exit 1
echo "JAVA_HOME=$JAVA_HOME"
if [ ! -f local.properties ]; then
  for s in "$ANDROID_HOME" "$HOME/.homebrew/share/android-commandlinetools" /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk"; do
    [ -n "$s" ] && [ -d "$s/platforms" ] && { echo "sdk.dir=$s" > local.properties; break; }
  done
fi
cat local.properties 2>/dev/null
./gradlew --console=plain "${@:-assembleDebug}"
