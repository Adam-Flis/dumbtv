# AGENTS.md: instructions for an AI agent

This repository is a fork of humblemuzzu/dumbtv. Its product is **dumbtv home**, a home screen
(launcher) for Amazon Fire TV in `launcher/`. This file tells an agent how to **set it up on
someone's TV** (part 1) and how to **change the code** (part 2). The human version of part 1 is
[README.md](README.md); follow the same steps and use the same words when you explain them.

The debloat scripts in `scripts/` come from upstream and are **not part of this setup**. Their
own contract is [docs/DEBLOAT-AGENTS.md](docs/DEBLOAT-AGENTS.md).

## Hard rules

1. **Never run `scripts/all.sh` or `scripts/debloat.sh`** unless the user asks to debloat in
   those words and you have warned them: on a Fire TV Edition **television** running Fire OS 6
   they disable the Live TV and HDMI input packages the TV needs.
2. **Never touch `/system`, never `adb root` / `adb remount`, never factory-reset.**
3. **Ask before pressing remote keys** (`adb shell input keyevent ...`): someone may be watching
   the TV. Installing, reading settings and taking screenshots need no warning.
4. **Change TV settings only through `launcher/install.sh`.** Anything else (`settings put`,
   `pm grant`, `dpm`, `appops`, disabling packages) needs the user's explicit yes first.
5. **Keep `launcher/release.keystore`.** Never delete, regenerate or commit it. Every update must
   be signed with the same key, or Android refuses it and the user loses their settings.
6. **Keep identifiers private.** IP addresses, serial numbers, Wi-Fi names and emails never go
   in a commit, an issue or a shared screenshot.
7. **One TV at a time.** If `adb devices` lists several, set `ADB_SERIAL=<serial>` for every
   command and script.
8. **In scripts, give `adb shell` an empty stdin** (`adb shell '...' </dev/null`) and quote the
   remote command as one string. Without it a remote pipe can wait on your terminal forever.
9. **The human does the steps on the TV screen** (enabling ADB debugging, accepting the "Allow
   USB debugging?" prompt). Do not retry `adb connect` in a loop while you wait for them.

## Part 1: set it up on a TV

### Step 0: ask first

Ask, in plain words, and wait for the answers:

1. "Which TV is it, and what does Settings › My Fire TV › About say for the software version?"
   (Fire OS 6 is tested; 7 should work; 8 and non-Amazon TVs are not supported. Stop for those
   and point to [docs/OTHER-TVS.md](docs/OTHER-TVS.md).)
2. "Which computer are we working on: Mac, Linux or Windows?" (Windows: use WSL.)
3. Optional, for after the install: the ZIP code for the weather, when night mode should start
   and end, and which apps go in Favorites. They can also set these on the TV themselves.

### Step 1: tools

Check what is there before installing anything:

```sh
adb version                  # Android Debug Bridge ...
java -version                # 17 or newer
ls "$ANDROID_HOME/platforms" 2>/dev/null || ls /opt/homebrew/share/android-commandlinetools/platforms
```

`launcher/sdk.sh` looks for the SDK in `$ANDROID_HOME`, then
`/opt/homebrew/share/android-commandlinetools`, then `~/Library/Android/sdk`, and for a JDK in
`$JAVA_HOME`, then Homebrew's `openjdk@17`. The build needs `platforms;android-25` (Fire OS 6),
`platforms;android-28` (Fire OS 7) and any `build-tools`. Install commands per OS are in
README step 2; run them only with the user's OK (they install software on their computer).

### Step 2: the user turns on ADB debugging

Tell them: Settings › My Fire TV › About, press OK on the TV's name 7 times, Back, Developer
options › ADB debugging › On. Then Settings › My Fire TV › About › Network shows the IP address.

### Step 3: connect

```sh
adb connect <TV_IP>:5555
adb devices                  # the TV's line must end in "device"
```

`unauthorized` means the TV shows "Allow USB debugging?": ask the user to tick Always allow and
press OK, then check again. Then identify the TV:

```sh
adb shell getprop ro.product.manufacturer </dev/null   # Amazon
adb shell getprop ro.build.version.sdk </dev/null      # 25 = Fire OS 6, 28 = Fire OS 7, 30 = Fire OS 8
```

Stop on anything but Amazon with SDK 25 or 28, and say why.

### Step 4: install

```sh
./launcher/install.sh            # builds from source, installs, takes HOME, allows the extras
```

It takes 1-3 minutes (the build runs the logic tests first). Success on Fire OS 6 ends with:

```
installed tv.dumbtv.home
sleep timer: allowed
screen saver in apps: allowed
home: dumbtv (Keeper, no accessibility; sdk 25)
```

On Fire OS 7 it ends with `accessibility: ...tv.dumbtv.home...` and `front window: ...`.
A first run also prints `created signing key .../release.keystore`: tell the user to keep a copy.

| `install.sh` says | Cause | Do |
|---|---|---|
| `no TV connected` | adb lost the TV | Step 3 again. |
| `android.jar missing` / `no build-tools` / `no JDK found` | tools missing | Step 1. |
| `signed with a different key` | the TV has a build from another key | With the user's OK: `./launcher/tools/settings.sh backup`, `adb uninstall tv.dumbtv.home`, install again, `settings.sh restore`. |
| `the Fire OS 6 home keeper did not start` | the service was refused | Run `install.sh` again once; then read `adb logcat -d \| grep -i keeper`. |
| `Fire OS did not bind the HOME guard` | Fire OS 7 refused the accessibility service | Unsupported build: say so; `install.sh` already undid its changes. |
| `note: the sleep timer could not be allowed` | `dpm` refused (another admin, or a managed TV) | Optional feature; it falls back to Fire TV's own sleep timer. |
| `note: the screen saver will only run on the home screen` | `pm grant` refused | Optional feature; nothing else is affected. |

### Step 5: verify

```sh
adb shell dumpsys activity activities </dev/null | grep mResumedActivity      # tv.dumbtv.home/.Home
adb shell dumpsys activity services tv.dumbtv.home </dev/null | grep isForeground   # Fire OS 6: isForeground=true
adb shell dumpsys device_policy </dev/null | grep tv.dumbtv.home               # the sleep timer admin
adb shell dumpsys package tv.dumbtv.home </dev/null | grep WRITE_SECURE        # granted=true
adb shell settings get secure screensaver_components </dev/null              # tv.dumbtv.home/.FrameDream
adb exec-out screencap -p > home.png                                          # look at it; do not share it
```

`screensaver_components` is written when the home screen first opens, not by the installer.
Then ask the user to press Home on the remote: the home screen should appear within a second.

### Step 6: their preferences

Easiest: the user sets them on the TV in Options (sliders button). Tell them where:

| Want | On the TV |
|---|---|
| Favorites | end of the Favorites row › Edit favorites |
| Weather | Options › Weather: on, then enter the ZIP code |
| Screen saver | Options › Screen saver: start after 1-15 min, Sleep after saver 15 min-2 h, Adjust overlay |
| Night mode | Options › Night: schedule, start, end, dim |
| Row order | Options › Home screen › Home screen layout |
| Check everything | Options › Help: every dot green |

You can also edit the settings file, a plain `key=value` file the home screen re-reads whenever
it comes back to the front:

```sh
adb pull /sdcard/Android/data/tv.dumbtv.home/files/home.conf
# edit it, then:
adb push home.conf /sdcard/Android/data/tv.dumbtv.home/files/home.conf
```

| Key | Values (default first) |
|---|---|
| `frame` | screen saver start: `off`, `1`, `2`, `5`, `10`, `15` (minutes) |
| `frame.for` | Sleep after saver: `30`, `15`, `45`, `60`, `90`, `120` (minutes) |
| `frame.clock`, `frame.weather` | corner: `bl` / `br`, `tl`, `tr`, `off` |
| `care` | OLED screen care: `on`, `off` |
| `weather` | `off`, `on`; also needs `weather.lat`, `weather.lon`, `weather.place` (display name), `weather.city` (the ZIP) |
| `units` | `f`, `c` |
| `night` | schedule: `off`, `on`; `night.start` / `night.end` as `HH:MM`, `night.dim` 10-90 |
| `favorites`, `apps.order`, `rows.order` | comma-separated package ids / row kinds (`favs,recent,apps,inputs`) |

`launcher/src/tv/dumbtv/home/Config.java` is the full list (every `Choice`). For the weather
coordinates of a ZIP code: `curl "https://geocoding-api.open-meteo.com/v1/search?name=<ZIP>&count=5&format=json"`,
and confirm the town with the user when several countries match.

Their own photos for the screen saver: `./launcher/tools/push-photos.sh <folder>`.
Save their settings: `./launcher/tools/settings.sh backup` (kept in `launcher/tools/backups/`,
git-ignored).

### Step 7: finish

Tell the user to turn ADB debugging off (Developer options) and give them
[launcher/GETTING-STARTED.md](launcher/GETTING-STARTED.md) for the household.

### Later

- **Update:** ADB on, `git pull`, `adb connect`, `./launcher/install.sh`. Settings stay.
- **Undo:** `./launcher/install.sh --undo` (Home, screensaver and sleep timer permission back to
  Amazon), then `adb uninstall tv.dumbtv.home` if they want it gone.
- **Home opens Amazon's home again:** run `install.sh` again.

## Part 2: change the code

### Ground rules

- **Plain Java and Android views, no libraries**: no Gradle, AndroidX, Kotlin or Compose.
  `launcher/build.sh` calls aapt2, javac, d8, zipalign and apksigner directly.
- **Fire OS 6 is Android 7.1 (API 25).** Build with `MIN_SDK=25` so javac rejects newer Android
  APIs; Java code is compiled with `--release 8`.
- **Keep it simple.** One file per job, small methods, comments that say *why*. The owner
  prefers fewer, clearer options over more of them.
- **One place for each thing:** settings and their allowed values in `Config` (a `Choice` each,
  value `Config.OFF` for off/hidden); colors and shapes in `Ui`; every dialog through `Popup`
  on `Panel` with `ValueRow` rows; top-bar menus through `Dropdown`; picture decoding in `Images`.
- **Options wording:** names and values of one or two words, no commas.
- **Animations are off on these TVs** (animator scale 0): fades use `Tween`, which draws frame
  by frame.
- **Test pure logic** in `launcher/test/tv/dumbtv/home/LogicTest.java` (plain JVM, android.jar
  stubs only, so no Android runtime calls).
- **Commit after each working change**, with a message that says what the user will notice.

### Build, test, install

```sh
./launcher/test.sh                              # logic tests, no TV needed
MIN_SDK=25 ./launcher/build.sh                  # → launcher/build/dumbtv-home.apk (runs the tests first)
./launcher/install.sh                           # build + install on the connected TV
```

### Testing on the TV

- Screenshot: `adb exec-out screencap -p > shot.png`. What is in front:
  `adb shell dumpsys activity activities </dev/null | grep mResumedActivity`.
- Crashes: `adb logcat -d -b crash </dev/null`; the app logs with tag `dumbtv`.
- Developer aids: `adb shell touch /sdcard/Android/data/tv.dumbtv.home/files/<name>`, then bring
  the home screen back (`adb shell am start -n tv.dumbtv.home/.Home </dev/null`):
  `test-input` shows the "switch input?" card, `test-sleep` starts a 1-minute sleep timer,
  `test-rest` turns the screen black (screen care) after 3 s.
- Screen saver: Fire TV starts it after `screen_off_timeout` without a key press;
  `adb shell dumpsys power </dev/null | grep mWakefulness` says `Dreaming` while it runs. A shell
  cannot start a dream on demand.
- Options is not exported, so `am start` cannot open it; reach it with key presses (rule 3).

### Map of the code (`launcher/src/tv/dumbtv/home/`)

| File | Job |
|---|---|
| `Home.java` | the home screen activity: header, top bar, rows, tile menu, move mode, screen saver timer |
| `WeatherHeader.java`, `InputCard.java` | the weather beside the clock; the "switch input?" card |
| `Tile.java`, `Grid.java`, `Icons.java`, `Ui.java`, `Tween.java` | drawing: tiles, wrapping grid, vector icons, shared look, animations |
| `Catalog.java`, `Item.java` | which apps and inputs exist, which show, in what order |
| `Config.java` | `home.conf`: every setting, its values and defaults |
| `Options.java`, `RowsEditor.java`, `TimeChooser.java`, `Picker.java` | the Options screen and its editors; the app picker |
| `Popup.java`, `Panel.java`, `ValueRow.java`, `Dropdown.java` | every dialog and menu |
| `PhotoFrame.java`, `FrameDream.java`, `SystemSaver.java`, `ScreenCare.java`, `Images.java` | screen saver view, Fire TV screensaver service, its timers, OLED care, picture decoding |
| `Weather.java`, `Clock.java` | Open-Meteo forecast and lookup; time and date text |
| `Sleep.java`, `Admin.java` | sleep timer alarm; the device admin that turns the TV off |
| `Keeper.java`, `Guard.java`, `BootReceiver.java` | taking the Home button: Fire OS 6 (UsageStats), Fire OS 7 (accessibility); restart after boot/update |
| `Help.java`, `FireSettings.java` | Options › Help checks; Fire TV settings screens other apps may open |

More detail, including how the Home button takeover works, is in
[launcher/README.md](launcher/README.md). Upstream's lessons are in
[docs/GOTCHAS.md](docs/GOTCHAS.md).
