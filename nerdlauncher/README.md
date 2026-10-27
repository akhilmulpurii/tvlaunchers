# Nerd Launcher

A text-only Android TV home screen in the style of an instrument dashboard: one monospace Nerd Font, no images,
no graphics. Package `com.akhilmulpuri.nerdlauncher`, `minSdk` 26, no network permission, no dependencies.
Release APK is about 65 KB.

- **Header:** date; Inputs (only when the TV has HDMI/AV inputs), network name, Settings.
- **Readouts:** uptime badge, big clock, date and week, and a table for the focused app (last opened, launches in
  the last 7 days) plus the Android version. RAM and storage free below.
- **Buttons:** every TV app A-Z, four per row; the first row is the dock (bigger buttons). The D-pad moves exactly one row or column,
  edges hold, Up from the dock goes to the header, and focus returns to the app you left.
- **Hold OK on an app** for Move and Uninstall (Uninstall only for apps you installed). Move: the app
  pulses, Left/Right one place and Up/Down one row shift it, OK or Back drops it. The order is saved; new apps join the end.
- **Screensaver:** `NerdDream`, the clock and readouts on their own, with a blinking cursor. The block drifts a
  little every minute against burn-in. Any key wakes.
- **Light by design:** updates on the minute tick, network changes, package changes and return to Home only.
  No polling, no blur, no animation beyond a short focus scale and the cursor blink.

## App colours

`AppColors.java` maps common apps (Netflix, YouTube, Prime Video, Hulu, Disney+, aha, ZEE5, Cricbuzz, Plex,
Jellyfin, Spotify and more) to their brand colour, tuned to read on the dark background. An unknown app gets
a colour sampled once from its banner (a 12x12 render) and cached until the app updates. Add an app by adding
a `put(color, "package.name")` line.

## Configure

`app/src/main/res/values/config.xml`:

- `hidden_apps`: packages never shown.
- `columns`: buttons per row (and dock size).

Palette: `app/src/main/res/values/values.xml`. Font: `app/src/main/res/font/nerd_mono.ttf`, ShureTechMono Nerd
Font Mono (Nerd Fonts' patch of Share Tech Mono, SIL OFL 1.1, see `FONT-LICENSE-OFL.txt`), subset to ASCII,
Latin-1 and the icons used, 37 KB.

## Build and install

Part of the [tvlaunchers](../README.md) repo; build from the repo root. Needs JDK 17 and Android SDK
platform 34; the scripts find the JDK on macOS.

```bash
tools/build.sh nerdlauncher             # debug build, signed with the dev key in nerdlauncher/signing/
tools/release.sh                       # release APKs for all three launchers, into dist/
adb install -r dist/nerdlauncher-0.1-universal.apk
adb shell pm grant com.akhilmulpuri.nerdlauncher android.permission.ACCESS_FINE_LOCATION   # Wi-Fi name
```


**Make it Home** (Google TV): install, open it once, then
`adb shell cmd package set-home-activity --user 0 com.akhilmulpuri.nerdlauncher/.MainActivity`. If another
launcher is still the default, that launcher has to be disabled; on Google TV, check the setup wizard
(`com.google.android.tungsten.setupwraith`) as described in the cleanyourtv repo before disabling it.
Fire TV keeps its own Home; there it runs as a normal app.

**Screensaver:** pick "Nerd Screensaver" in the TV's screensaver settings, or
`adb shell settings put secure screensaver_components com.akhilmulpuri.nerdlauncher/.NerdDream`.
Debug builds include a preview: `adb shell am start -n com.akhilmulpuri.nerdlauncher/.DreamPreviewActivity`.

## Testing from a sandboxed assistant

`tools/relay.sh` (repo root) runs in a normal terminal and executes only the `adb` calls and builds queued in `.relay/`
(git-ignored), for coding assistants whose sandbox can't reach the TV or run Gradle.

## Banner and icon

The only images, drawn from the font: `python3 tools/make-art.py <font.ttf> <out dir>` writes `banner.png`
(640x360) and `ic_launcher.png` (192x192).
