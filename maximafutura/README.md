# Maxima Futura

![screenshot](../screenshots/web/futura.jpg)

A minimal HUD-style Android TV home screen: dark matrix green, frosted hex shapes, one typeface. Package
`com.akhilmulpuri.maximafutura`, `minSdk` 26, no network permission, no dependencies.

- **Tabs** along the top: network (name and signal), Settings, and Inputs when the TV has HDMI/AV inputs.
- **Clock** on the left, **the selected app** on the right with when it was last opened and launches in the
  last 7 days.
- **Rail** of text tiles along the bottom: every TV app A-Z. Long
  names end in "…". The D-pad moves one step at a time, edges hold, Up goes to the tabs, focus returns to the
  app you left.
- **Hold OK on an app** for Move and Uninstall (Uninstall only for apps you installed). Move: the app
  pulses, Left/Right shift it, OK or Back drops it. The order is saved; new apps join the end.
- **Screensaver** "Maxima Futura Rain": cmatrix-style digital rain (half-width katakana when the TV's fonts
  have them, letters and digits otherwise) behind a frosted clock plate that drifts each minute.

## Performance notes

- Home updates on events only: the minute tick, network and package changes, return to Home. The backdrop
  (vignette and grid) is drawn once into a GPU layer.
- The rain renders glyphs once into an atlas and copies cells from it each frame, grouped by brightness, at
  about 15 fps. Measured on a Chromecast with Google TV: ~26 ms per frame and ~20% of one core while it runs.
  It runs only while the TV is idle.

## Configure

`app/src/main/res/values/config.xml`: `hidden_apps`. Palette: `Hud.java` and `values.xml`.

Font: Oxanium (SIL OFL 1.1, `FONT-LICENSE-OFL.txt`), variable weight, subset to Latin, 27 KB.

## Build and install

Part of the [tvlaunchers](../README.md) repo; build from the repo root. Needs JDK 17 and Android SDK
platform 34; the scripts find the JDK on macOS.

```bash
tools/build.sh maximafutura             # debug build, signed with the dev key in maximafutura/signing/
tools/release.sh                       # release APKs for all three launchers, into dist/
adb install -r dist/maximafutura-0.1-universal.apk
adb shell pm grant com.akhilmulpuri.maximafutura android.permission.ACCESS_FINE_LOCATION   # network name
```

**Make it Home** (Google TV): `adb shell cmd package set-home-activity --user 0 com.akhilmulpuri.maximafutura/.MainActivity`
once the stock launcher is out of the way (see the Nerd Launcher README). Fire TV keeps its own Home.

**Screensaver:** pick "Maxima Futura Rain" in the TV's screensaver settings, or
`adb shell settings put secure screensaver_components com.akhilmulpuri.maximafutura/.RainDream`.
Debug builds include a preview: `adb shell am start -n com.akhilmulpuri.maximafutura/.RainPreviewActivity`.

Banner and icon: `python3 tools/make-art.py app/src/main/res/font/oxanium.ttf <out dir>`.
