# Setting this up on someone else's TV

This guide is for whoever does the install: you need a computer and about 30 minutes.
The people who watch the TV only need [GETTING-STARTED.md](GETTING-STARTED.md). Print it
or send it to them.

## Will it work on their TV?

Check the TV's software first: Settings › My Fire TV (or Device & Software) › About.

| TV | Home screen | Notes |
|---|---|---|
| **Fire TV Edition TV or stick, Fire OS 6** (Android 7.1) | Yes, tested | Built and tested here on a Toshiba Fire TV (Fire OS 6.7.1.1). |
| **Fire TV, Fire OS 7** (Android 9) | Yes | The original dumbtv launcher was built on Fire OS 7. This fork's extras (sleep timer, weather, screen saver, screen care) haven't been tried on it yet. |
| **Fire TV, Fire OS 8** (Android 11) | Not yet | Android 11 blocks the Home button hand-over. See `docs/GOTCHAS.md`. |
| **Google TV / Android TV** (Sony, TCL, Hisense, Philips, Shield, Chromecast) | Untested | The upstream method is to disable the stock launcher. The Fire-specific parts (Keeper, Guard, Amazon settings shortcuts) won't apply. |
| **Samsung, LG, Roku, Vizio, VIDAA, Panasonic** | No | These TVs don't let anyone install a home screen. Block ads at the router instead (`docs/OTHER-TVS.md`). |

## What you need

- A Mac, Linux or Windows (WSL) computer on the same Wi-Fi as the TV.
- `adb`: `brew install android-platform-tools` on a Mac.
- To build from source (needed on Fire OS 6 until a prebuilt release exists, see below): JDK 17 and the Android SDK. `launcher/README.md` lists the exact steps.
- Never run `scripts/all.sh` or `scripts/debloat.sh` on a Fire TV Edition **television**. The Fire OS 6 list disables the Live TV and HDMI input packages that a television needs.

## Steps

1. **Turn on ADB debugging on the TV.** Settings › My Fire TV › About: click the device name 7 times. Then go to Developer options and turn on ADB debugging.
2. **Find the TV's IP address.** Settings › My Fire TV › About › Network.
3. **Connect:** `adb connect <tv-ip>:5555`. Then accept "Allow USB debugging?" on the TV and tick "Always allow".
4. **Install:** `./launcher/install.sh --build`. It installs the home screen and makes the Home button open it.
5. **Allow the sleep timer** (one time): `adb shell dpm set-active-admin tv.dumbtv.home/.Admin`.
6. **Set it up with them, on the TV:**
   - Favorites: Edit favorites at the end of the row.
   - Weather city: Options › Weather.
   - Night mode times: Options › Night.
   - Their own photos: `launcher/tools/push-photos.sh <folder>` from the computer.
7. **Let the screen saver work in apps too** (one time):
   `adb shell pm grant tv.dumbtv.home android.permission.WRITE_SECURE_SETTINGS`.
   Then Options › Screen saver sets when it starts and when the TV sleeps, everywhere.
8. **Check Options › Help:** every dot should be green.
9. **Save their settings:** `launcher/tools/settings.sh backup`.
10. **Turn ADB debugging off again** (Developer options). It stops anyone else on the network from controlling the TV.


## Updating later

Turn ADB debugging on, run `adb connect`, then `./launcher/install.sh --build` again. Settings, favorites and photos stay. Every update must be signed with the same `launcher/release.keystore`, so back it up. It isn't in git. If you sign with another key, Android refuses the update, and the old app has to be removed first, which loses its settings.

## Undo

`./launcher/install.sh --undo` gives the Home button back to Amazon and removes the sleep-timer permission. After that, the app can be uninstalled like any other.

## Privacy, in one paragraph

The home screen shows no ads and collects nothing. It goes online only for the weather: it sends the city's coordinates to Open-Meteo, which needs no account or key. Turn weather off and it never goes online.

## Not done yet (contributions welcome)

- **A prebuilt download for Fire OS 6.** `install.sh` builds from source on Fire OS 6. On Fire OS 7/8 it downloads the upstream release, which doesn't include this fork's extras. Publishing this fork's APK as a GitHub release (and pointing `version.env` at it) would remove the JDK/SDK step.
- **Setup without a computer.** Fire OS only lets a computer grant the Home button hand-over and the sleep timer permission. On Fire OS 6, Options › Help offers to open the usage-access setting, which may be enough for the Home button. This hasn't been tested yet.
- **Testing on Fire OS 7 and Google TV.**
