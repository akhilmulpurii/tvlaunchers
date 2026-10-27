# Sourced by the other scripts: finds a JDK 17+ on macOS (JAVA_HOME, java_home, Nix, Homebrew, Android Studio).
cands=("$JAVA_HOME"
  "$(/usr/libexec/java_home -v 17 2>/dev/null)" "$(/usr/libexec/java_home -v 21 2>/dev/null)"
  "$(nix eval --raw nixpkgs#jdk17.home 2>/dev/null)"
  "$HOME/.nix-profile/lib/openjdk" "$HOME/.nix-profile"
  "$HOME/.homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" "$HOME/.homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
  "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home")
for d in /Library/Java/JavaVirtualMachines/*/Contents/Home; do cands+=("$d"); done
JH=""
for c in "${cands[@]}"; do [ -n "$c" ] && [ -x "$c/bin/java" ] && { JH="$c"; break; }; done
[ -n "$JH" ] || { echo "No JDK found. Tried:"; printf '  %s\n' "${cands[@]}"; exit 1; }
export JAVA_HOME="$JH" PATH="$JH/bin:$PATH"
LAUNCHERS=(nerdlauncher maximafutura zunesthetic)
