# Zunesthetic

A Zune / Windows Phone style Android TV home screen: one huge lowercase vertical list over a wallpaper, no icons.
Package `com.akhilmulpuri.zunesthetic`, `minSdk` 26, no network permission, no dependencies.

- **Controls first**, slightly greyed: inputs (only when the TV has HDMI/AV inputs), wi-fi (with the network
  name), settings. Then a divider and **every TV app, A-Z**.
- **Vertical only.** Up and Down move one row; the focused row stays at the same height and the list glides
  under it. The focused row is white with a small caption (network name, or when an app was last opened);
  the rest fade back. Left and Right do nothing; the ends hold.
- **Coming back to Home** the rows slide in from the right one after another (Metro turnstile), and focus
  returns to the app you left. Home pressed on Home jumps to the first app.
- **Hold OK on an app** for Move and Uninstall (Uninstall only for apps you installed). Move: the app
  pulses, Up/Down (never above the divider) shift it, OK or Back drops it. The order is saved; new apps join the end.
- **Clock and date** top right.
- **Screensaver** "Zunesthetic": the wallpaper slowly zooming and panning, the day and month drifting across in
  huge faint type, and a lock-screen clock bottom left that shifts a little each minute. About 30 fps, ~22% of
  one core on a Chromecast with Google TV.

## Wallpaper

`app/src/main/res/drawable-nodpi/bg.jpg`, 1920x1080. The original (`zune-bg.png`) is git-ignored; to change it,
save a 16:9 JPEG at 1920x1080 over `bg.jpg` and rebuild. A left-side gradient (`drawable/scrim.xml`) keeps the
list readable on light pictures. Wallpaper and gradient are cached as one GPU layer.

## Configure

`app/src/main/res/values/config.xml`: `hidden_apps`. The system Settings apps are hidden by default because the
"settings" control already opens them.

Font: Selawik (Microsoft's open Segoe stand-in, SIL OFL 1.1, `FONT-LICENSE-OFL.txt`), Light and Regular,
compiled from the official UFO sources with fontmake and subset to Latin, 22 KB each.

## Build and install

Part of the [tvlaunchers](../README.md) repo; build from the repo root. Needs JDK 17 and Android SDK
platform 34; the scripts find the JDK on macOS.

```bash
tools/build.sh zunesthetic             # debug build, signed with the dev key in zunesthetic/signing/
tools/release.sh                       # release APKs for all three launchers, into dist/
adb install -r dist/zunesthetic-0.1-universal.apk
adb shell pm grant com.akhilmulpuri.zunesthetic android.permission.ACCESS_FINE_LOCATION   # network name
```

**Make it Home** (Google TV): `adb shell cmd package set-home-activity --user 0 com.akhilmulpuri.zunesthetic/.MainActivity`
once the stock launcher is out of the way (see the Nerd Launcher README). Fire TV keeps its own Home.

**Screensaver:** pick "Zunesthetic" in the TV's screensaver settings, or
`adb shell settings put secure screensaver_components com.akhilmulpuri.zunesthetic/.ZuneDream`.
Debug builds include a preview: `adb shell am start -n com.akhilmulpuri.zunesthetic/.DreamPreviewActivity`.

Banner and icon: `python3 tools/make-art.py app/src/main/res/drawable-nodpi/bg.jpg app/src/main/res/font/selawik_light.ttf <out dir>`.
