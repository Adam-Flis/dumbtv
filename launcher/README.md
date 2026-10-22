# dumbtv home: how it works

The technical reference for the home screen in this folder. To set it up, read the
[README](../README.md) (people) or [AGENTS.md](../AGENTS.md) (AI agents). To use it, read
[GETTING-STARTED.md](GETTING-STARTED.md).

- **Plain Java and Android views.** No Gradle, AndroidX, Kotlin, Compose or other libraries;
  `build.sh` calls the SDK tools directly. The APK is about 5 MB, nearly all of it the 12
  built-in screen saver photos.
- **Runs on Fire OS 6 (Android 7.1, API 25)** and is built against API 25 there, so it cannot
  call anything newer by accident.
- **Permissions:** network and Wi-Fi state (read-only, for the top bar's Wi-Fi button), internet
  (only the optional weather connects), and on Fire OS 6 usage access, boot and task order (the
  Home button keeper). Two optional grants come from `install.sh`: device admin with only the
  "lock screen" policy (the sleep timer) and WRITE_SECURE_SETTINGS (the screen saver in apps).
- **No ads, no account, no analytics, no update checks.**

## Features

- **Rows:** Favorites (with an Edit favorites tile at the end), Recently used (the last 5 apps,
  from UsageStats), All apps (Grid or Scroll) and Inputs, in any order; every row but All apps can
  be hidden (Options › Home screen › Home screen layout). Hold OK or press ☰ on a tile: Add to /
  Remove from Favorites, Move, Rename, Hide, App info. All apps ends with Get more apps (the
  Appstore) and App library (every installed app, to show or hide).
- **Header:** greeting, large clock and date, the weather now (icon, temperature, conditions,
  high/low, feels like and wind), the next 5 hours, and the next sunrise/sunset with tomorrow.
  Options › Weather details picks how much (Everything / Next hours / Today / Temperature).
- **Top bar:** Inputs (a dropdown; a green dot means something is plugged in and on), Wi-Fi (name
  and a green / amber / red status), Night mode, Sleep timer, Options, and Settings (a dropdown
  of the Fire TV settings screens other apps are allowed to open, plus All Fire TV settings).
- **Background:** the focused app's color as a soft glow (default), black, graphite, midnight, or
  your own images with a dim level and a rotation (each return, hourly, daily, by time of day).
- **"Switch input?" card:** when a device on an HDMI input turns on while the home screen shows
  (Options › Home screen › Detect input). It goes away after 15 s.
- **Sleep timer:** 15 min to 2 h, or Custom (up to 9 h 55). While it runs the button shows the
  minutes left and its menu adds 15 or 30 minutes, sets a new length or cancels. The alarm turns
  the TV off through the device admin; without that permission the button explains the
  one-time setup and offers Fire TV's own sleep timer.
- **Night mode:** a warm dim over everything, on a schedule (Options › Night: start, end, 10-90 %
  dim) or with the top bar's button until the schedule's next change. No screen saver at night.
- **Screen saver, everywhere:** photos cross-fade every 20 s under the clock and the weather,
  each in any corner or hidden (Options › Screen saver › Adjust overlay). Options › Screen saver
  sets when it starts (Off, 1-15 min) and **Sleep after saver** (15 min-2 h, then the TV sleeps).
  With the WRITE_SECURE_SETTINGS grant it is Fire TV's own screensaver (`FrameDream`), so it also
  starts over a paused app, and `SystemSaver` keeps Fire TV's timers in step with Options:

  | Fire TV setting | Value |
  |---|---|
  | `screensaver_components` | `tv.dumbtv.home/.FrameDream` (with `screensaver_enabled=1`, `screensaver_activate_on_sleep=1`) |
  | `screen_off_timeout` (system) | the screen saver's start time; 5 min with it off |
  | `sleep_timeout` (secure) | start + Sleep after saver + 5 min, a backstop; the screen saver itself ends first |

  At night the screensaver stays black and the TV sleeps at the 5-minute mark. Without the grant
  the home screen runs the same screen saver itself, and apps follow Fire TV's own timers.
  Pictures come from `photos/` next to `home.conf` (`tools/push-photos.sh`), or the bundled ones
  (`assets/photos/CREDITS.txt`).
- **Screen care (OLED)** (Options › Screen saver, on by default): the home screen and the screen
  saver's clock and weather move up to 2 dp to one of nine spots every 3 minutes, the screen
  saver's text is drawn at 85 % brightness, and when the home screen's own screen saver ends the
  screen goes black until the TV sleeps.
- **Options**, grouped on the left: Home screen, Appearance, Weather, Screen saver, Night, More,
  Help. Values are one or two words; rows that depend on another setting appear only when it is
  on. **Options › Help** shows green or amber checks (Home button, Sleep timer, Screen saver in
  apps, Internet, Weather; OK on an amber one explains the fix) and a remote-button guide.
- **Weather** (off by default) from [Open-Meteo](https://open-meteo.com): no account, no key, only
  the coordinates are sent. Enter a ZIP code; when several countries share it, pick the town. The
  forecast refreshes every 30 minutes and is hidden if it could not be refreshed for 3 hours.
- **Tools for your computer** (`tools/`): `push-photos.sh <folder>` resizes photos to 1080p and
  sends them (`--replace`, `--list`, `--clear`); `settings.sh backup|restore|list` keeps copies of
  `home.conf` in `tools/backups/` (git-ignored).

## The settings file

Everything is in one plain text file on the TV:

```
/sdcard/Android/data/tv.dumbtv.home/files/home.conf
```

One `key=value` per line; lines starting with `#` are comments. The home screen re-reads the
file whenever it comes back to the front, so `adb pull`, an edit and `adb push` is a complete
export, edit and import. Unknown keys are kept. Every key and its allowed values is a `Choice` in
`src/tv/dumbtv/home/Config.java`; the common ones are listed in [AGENTS.md](../AGENTS.md).
Lists are comma-separated package ids:

```
favorites=com.amazon.firetv.youtube,com.netflix.ninja
apps.order=com.spotify.tv.android,org.videolan.vlc
apps.hide=org.videolan.vlc
apps.show=com.amazon.venezia
inputs.hide=com.droidlogic.tvinput/.services.AV1InputService/HW1
name.com.droidlogic.tvinput/.services.Hdmi2InputService/HW6=PS5
```

For a wallpaper set `background=image` and push `wallpaper.jpg` next to the file, or several
images into `wallpapers/` with a `rotate=` mode. With `rotate=daytime`, a file named with
`morning` or `day` shows 06:00-13:00, `afternoon` or `evening` 13:00-20:00, `night` the rest.

## How HOME works on Fire OS

Fire OS 7 has no working "default launcher" setting, and Amazon's launcher is a
protected package. Three things combine to make HOME land here, measured on this TV:

1. **The HOME key can't be intercepted.** Fire OS handles it before any accessibility
   key filter runs (D-pad keys reached the filter; HOME never did). So `Guard`, a
   small accessibility service, watches for Amazon's home window instead. It reads
   no screen content and sees only window changes.
2. **Android delays app launches for 5 s after a HOME press** (`APP_SWITCH_DELAY_TIME`).
   This is what makes other launchers flash Amazon's home for 3–6 s. A launch fired
   by AlarmManager comes from `system_server`, which is exempt from that delay.
   AlarmManager normally refuses to fire sooner than 5 s ahead, so `install.sh` sets
   `settings global alarm_manager_constants min_futurity=0`. `--undo` removes it.
3. **Amazon's home is covered before it draws.** `Guard` keeps an overlay window ready
   (no surface while idle, so it costs nothing during video). It shows the overlay
   the moment Amazon's home appears, and removes it once our window is on screen.

What you see on HOME: the app, then about 0.3 s of black, then this home screen.

### Fire OS 6

Fire OS 6.7 goes further: its `A11yAuthorizer` refuses third-party accessibility
services, so `Guard` is never bound and HOME cannot be taken over that way. The
Fire OS 6 build therefore uses `Keeper`, a small foreground service that needs no
accessibility:

1. `UsageStats` (`appops set tv.dumbtv.home GET_USAGE_STATS allow`, granted by
   `install.sh`) tells it when Amazon's launcher reaches the foreground.
2. `ActivityManager.moveTaskToFront` brings the dumbtv task back. This is not an
   activity launch, so it is not held back by the 5 s app-switch delay: measured
   under 0.25 s with a real remote key.
3. At boot there is no dumbtv task yet, so the first start uses an alarm-fired
   `PendingIntent`; after that, `moveTaskToFront` takes over.

`Options > More > Amazon home screen` pauses the Keeper, so Amazon's home stays
until another app is opened.

## Built for the long run

The home screen and the screen saver run for weeks without a restart, so:

- Every timer, receiver and callback a screen registers is removed when it closes
  (`Home.onDestroy`, `FrameDream.onDetachedFromWindow`, `ScreenCare.stop`).
- At most two screen saver photos are decoded at a time, at screen size in RGB_565 (`Images`):
  a huge or oddly shaped picture is shrunk, and one that still does not fit in memory is skipped.
- The Fire OS 6 keeper checks the foreground app every 250 ms while the TV is on, every 5 s while
  it is off, and wakes at once when the TV turns on.
- The weather is fetched every 30 minutes, retried every 5 minutes after a failure, shared
  between the home screen and the screen saver, and hidden once it is 3 hours old.
- The once-a-minute clock tick only redraws what changed.
- A running sleep timer is set again after an app update (Android drops the alarm then).
- Settings are written to a temporary file and renamed over `home.conf`, so a power cut never
  leaves half a file.

## Files

| File | What it is |
|---|---|
| `src/.../Home.java` | the home screen: header, top bar, rows, tile menu, move mode, screen saver timer |
| `src/.../WeatherHeader.java` | the weather beside the clock |
| `src/.../InputCard.java` | the "switch input?" card |
| `src/.../Tile.java`, `Grid.java` | one tile drawn in a single `onDraw`; the wrapping All apps grid |
| `src/.../Catalog.java`, `Item.java` | which apps and inputs exist, and which are shown in what order |
| `src/.../Config.java` | the `home.conf` reader and writer, and every setting's values |
| `src/.../Options.java` | the Options screen |
| `src/.../RowsEditor.java`, `TimeChooser.java`, `Picker.java` | row order, time picker, app picker |
| `src/.../Ui.java`, `Icons.java`, `Tween.java` | shared look (color tokens, text, pills, dots, focus), drawn icons, frame-by-frame fades |
| `src/.../Panel.java`, `Popup.java`, `ValueRow.java`, `Dropdown.java` | every dialog (one card style), its rows, and the top-bar menus |
| `src/.../PhotoFrame.java` | the screen saver view: photos, clock, weather |
| `src/.../FrameDream.java` | the screen saver as Fire TV's screensaver, for idle apps |
| `src/.../SystemSaver.java` | keeps Fire TV's screensaver and idle timers in step with Options |
| `src/.../ScreenCare.java` | OLED care: drift, dimmer overlay, black after the screen saver |
| `src/.../Images.java` | picture types and decoding at screen size |
| `src/.../Weather.java`, `Clock.java` | Open-Meteo forecast and ZIP lookup; time and date text |
| `src/.../Sleep.java`, `Admin.java` | the sleep timer alarm; the device admin that turns the TV off |
| `src/.../Help.java`, `FireSettings.java` | Help checks and remote guide; the Fire TV settings screens other apps may open |
| `src/.../Guard.java` | the Fire OS 7/8 HOME takeover |
| `src/.../Keeper.java` | the Fire OS 6 HOME takeover (UsageStats + moveTaskToFront) |
| `src/.../BootReceiver.java` | restarts the keeper after a boot or an update; re-arms the sleep timer |
| `test/.../LogicTest.java`, `test.sh` | plain-JVM tests for pure logic (labels, row order, times, picture sizes, timers) |
| `sdk.sh` | finds the Android SDK and a JDK; shared by `build.sh` and `test.sh` |
| `build.sh` | tests, then aapt2 → javac → d8 → zipalign → apksigner |
| `install.sh` | build (or `--release`), install, HOME hook (Guard or Keeper), extras, `--undo` |
| `version.env`, `release-check.sh` | upstream's release version and checksum, and their check |

## Building

Needs JDK 17 and the Android SDK with `platforms;android-25` (Fire OS 6), `platforms;android-28`
(Fire OS 7) and any `build-tools` (README step 2 has the commands).

```sh
./launcher/test.sh                  # plain-JVM logic tests
MIN_SDK=25 ./launcher/build.sh      # → launcher/build/dumbtv-home.apk (Fire OS 6; 28 is the default)
./launcher/install.sh               # build for the connected TV and install
```

On Linux set `JAVA_HOME` and `ANDROID_HOME`; the defaults are Homebrew's paths. `build.sh`
creates `launcher/release.keystore` on its first run. Keep it: Android only accepts an update
signed with the same key.

## Releases

This fork has no prebuilt release: `install.sh` always builds. `install.sh --release` installs
upstream's prebuilt APK (Fire OS 7/8, checksum-pinned in `version.env`), which has none of this
fork's features and a different signing key.
