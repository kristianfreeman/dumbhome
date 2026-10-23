# dumbhome

A minimal Home screen for a Hisense Google TV that's really just a monitor for an Apple TV. It replaces the Google TV launcher with two tiles, one per HDMI input, and nothing else. No root needed, only ADB.

- **Tiles** for Apple TV and Gaming PC, with live connection status
- **Settings gear** in the corner, since the stock launcher is gone
- **Idle mode** after 2 minutes: the tiles fade and a clock/date bounces around the screen like a DVD logo, changing color on every bounce
- **Back** on Home returns to the last input; focus starts on it too
- **Restore Google TV** checklist at the bottom that walks through undoing everything

HDMI-CEC handles the common case: waking the Apple TV switches the TV to it, so the launcher rarely shows.

## Build

Needs JDK 17 and the Android SDK command-line tools (no Gradle):

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools android-platform-tools
sdkmanager --sdk_root="$HOME/Library/Android/sdk" "platforms;android-34" "build-tools;34.0.0"
./build.sh
```

`build.sh` creates `debug.keystore` on first run. Keep it: updates must be signed with the same key, or you have to uninstall first.

## Install

Google TV only exposes ADB over the network when **Wireless debugging** is on, and that only works on Wi-Fi (not Ethernet). Developer options → Wireless debugging → Pair device with pairing code, then:

```bash
adb pair <tv-ip>:<pairing-port> <code>
adb connect <tv-ip>:<port>
adb tcpip 5555   # optional: keeps ADB on :5555 until reboot, so you can go back to Ethernet
```

Install and make it Home:

```bash
adb install -r dumbhome.apk
adb shell cmd role add-role-holder android.app.role.HOME dev.kristian.dumbhome 0
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Both packages have to go: with only the launcher disabled, Home lands on setupwraith's recovery screen.

## Undo

From the TV: **Restore Google TV** at the bottom of the Home screen. Or over ADB:

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb uninstall dev.kristian.dumbhome
```

Selling the TV? A factory reset (Settings → System → Reset) undoes all of this.

## Adapting to another TV

The input IDs in `TILES` (`HomeActivity.java`) are specific to this set. On this Hisense the hardware IDs are offset from the port numbers: `HW2` is HDMI 1, `HW3` is HDMI 2. Find yours with:

```bash
adb shell dumpsys tv_input | grep inputId     # input IDs
adb shell dumpsys hdmi_control | grep CEC:    # which CEC device sits on which port
```

Hisense's Live TV app only accepts the passthrough `VIEW` intent when the MIME type is set explicitly (`vnd.android.cursor.item/channel`), so `openInput` sets it.

## Icons

Icons are [Phosphor](https://phosphoricons.com) (duotone), MIT licensed, see `licenses/phosphor-icons.txt`. To swap one, convert the SVG with:

```bash
python3 -I tools/svg2vd.py path/to/icon-duotone.svg res/drawable/ic_name.xml
```

`design/tiles.tsx` is the mockup the layout came from.
