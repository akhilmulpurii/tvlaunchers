#!/bin/bash
# Runs adb jobs queued in .relay/queue/ (one adb argument per line) and writes results to .relay/out/.
# Job "@build <launcher> [tasks]" runs tools/build.sh. Ctrl-C to stop.
cd "$(dirname "$0")/.." || exit 1
Q=.relay/queue; O=.relay/out
mkdir -p "$Q" "$O"
command -v adb >/dev/null || { echo "adb not on PATH"; exit 1; }
echo "relay running in $(pwd), Ctrl-C to stop"
adb devices
while true; do
  for f in "$Q"/*.job; do
    [ -e "$f" ] || continue
    id=$(basename "$f" .job)
    args=()
    while IFS= read -r l || [ -n "$l" ]; do args+=("$l"); done < "$f"
    rm -f "$f"
    echo "[$(date +%T)] $id: ${args[*]:0:4}"
    if [ "${args[0]}" = "@build" ]; then
      tools/build.sh "${args[@]:1}" > "$O/$id.out" 2>&1
    else
      adb "${args[@]}" > "$O/$id.out" 2> "$O/$id.err"
    fi
    echo $? > "$O/$id.rc.tmp" && mv "$O/$id.rc.tmp" "$O/$id.rc"
  done
  sleep 0.3
done
