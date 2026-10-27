# tvlaunchers

Three lightweight Android TV home screens, each a standalone Gradle project with no dependencies.
Built and tested on a Chromecast with Google TV (Google TV 14); they also run as normal apps on Fire TV.

| Launcher | Package | Look | Screensaver |
|---|---|---|---|
| [Nerd Launcher](nerdlauncher/) | `com.akhilmulpuri.nerdlauncher` | Text-only instrument dashboard, Nerd Font, per-app colours | Clock and readouts with a blinking cursor |
| [Maxima Futura](maximafutura/) | `com.akhilmulpuri.maximafutura` | Minimal frosted green HUD, hex tabs, text tiles | cmatrix-style digital rain |
| [Zunesthetic](zunesthetic/) | `com.akhilmulpuri.zunesthetic` | Zune / Windows Phone vertical list over a wallpaper | Drifting wallpaper, giant day and month, lock-screen clock |

All three: apps A-Z by default; **hold OK on an app** to move it or uninstall it, and the order is kept.

## Download

Signed release APKs are in [`dist/`](dist/), one universal APK per launcher (no native code, so the same
APK runs on arm, arm64 and x86 TVs). Checksums in `dist/SHA256SUMS`.

```bash
adb install -r dist/nerdlauncher-0.1-universal.apk
```

## Layout

```
nerdlauncher/  maximafutura/  zunesthetic/   one Gradle project each (own README)
tools/         build, release, keystore and relay scripts shared by all three
dist/          release APKs
keystores/     release keys and passwords (git-ignored, back up separately)
```

## Build

Needs JDK 17 and Android SDK platform 34; `tools/env.sh` finds the JDK on macOS (Nix, Homebrew, Android Studio).

```bash
tools/build.sh zunesthetic                 # debug APK, dev key
tools/build.sh nerdlauncher installDebug   # any Gradle task
tools/release.sh                           # all three release APKs into dist/, with SHA256SUMS
```

## Signing

- **Release:** `keystores/<launcher>-release.p12` (PKCS12, RSA 4096, valid 30 years) with random passwords in
  `keystores/keystore.properties`. Create them on a fresh clone with `tools/make-keystores.sh`. The folder is
  git-ignored; **back it up**: an app can only be updated in place with the key it was first installed with.
- **Dev:** `<launcher>/signing/*.jks` (password `android`, git-ignored), used for debug builds so they keep
  installing over each other. Without release keys, release builds fall back to it.

Debug and release builds of the same launcher have different keys, so switching a TV from one to the other
means uninstalling first (which clears its saved order and launch history).

## Relay

`tools/relay.sh` runs in a normal terminal and executes only the `adb` calls and `@build <launcher> [tasks]`
jobs queued in `.relay/` (git-ignored), for coding assistants whose sandbox can't reach the TV or run Gradle.
