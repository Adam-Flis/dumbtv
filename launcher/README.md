# dumbtv home

A home screen for Fire TV that does one job: show your apps and your HDMI inputs.

**Setting it up for someone else?** Read [SHARING.md](SHARING.md). **Watching a TV that has
it?** Read [GETTING-STARTED.md](GETTING-STARTED.md).

- **45 KB APK.** Plain Java and Android views: no AndroidX, Kotlin, Compose, Play
  Services or other libraries.
- **No network.** It has no `INTERNET` permission, so Android blocks it from
  opening any network connection. The APK declares usage access, reorder-tasks
  and boot-completed with `android:maxSdkVersion="27"`: Fire OS 6 requests them
  for the Keeper described below, Fire OS 7/8 does not. Check it yourself:
  `aapt2 dump permissions build/dumbtv-home.apk`.
- **No ads, no "recommended" rows, no account, no analytics, no update checks.**

## Install (no Java, no SDK)

You need `adb` and a TV with ADB debugging on (Settings → My Fire TV → About →
click the device name 7 times, then Developer Options → ADB debugging).

```sh
adb connect <tv-ip>:5555         # accept "Allow USB debugging?" on the TV
./launcher/install.sh            # downloads the APK, checks its SHA-256, installs, takes over HOME
```

That is all. The APK comes from this repo's
[releases](https://github.com/humblemuzzu/dumbtv/releases/latest); the script
refuses to install it if the checksum differs from the one pinned in
`install.sh`. Prefer to install by hand? Download `dumbtv-home.apk` from the
release and `adb install` it — but HOME only switches over after `install.sh`
(or `scripts/launcher.sh`) has installed the HOME hook described below: the
accessibility service and alarm setting on Fire OS 7/8, the UsageStats Keeper on
Fire OS 6.

Undo: `./launcher/install.sh --undo` gives HOME back to Amazon's launcher.

## Why not Projectivy

This repo used to install Projectivy. It was replaced because it is closed source
and asks for far more than a home screen needs: the advertising ID, ad
attribution, Play install referrer, internet, notification access and package
install/delete. Measured on an Onida Fire TV Edition (Fire OS 7.7, 1.4 GB RAM):

| | dumbtv home | Projectivy 4.72 |
|---|---|---|
| APK | 45 KB | several MB |
| Permissions requested | 0 (Fire OS 7/8); usage access (Fire OS 6) | 20+, incl. advertising ID |
| RAM, home on screen | 57–72 MB PSS | 135 MB PSS |
| RAM, while an app plays | ~37 MB PSS | — |
| HOME → home screen | 0.16–0.25 s | 3–6 s, Amazon's home shows first |

## Build it yourself

Needs a JDK 17 and the Android SDK (`platforms;android-28`, `build-tools`). No
Gradle: `build.sh` calls aapt2, javac, d8, zipalign and apksigner directly.

```sh
brew install openjdk@17
brew install --cask android-commandlinetools
sdkmanager "platforms;android-28" "build-tools;34.0.0"
./launcher/test.sh               # plain-JVM logic tests (build.sh runs them first)
./launcher/build.sh              # → launcher/build/dumbtv-home.apk
./launcher/install.sh --build    # build, install, take over HOME
```

On Linux set `JAVA_HOME` and `ANDROID_HOME`; the defaults are Homebrew's paths.

`build.sh` creates `launcher/release.keystore` the first time it runs. Your build
is signed with your key, the release with the project's, and Android will not
update one with the other: `install.sh` says so and tells you to
`adb uninstall tv.dumbtv.home` first (back up `home.conf` before, see below).

## This fork (`my-launcher`)

Built for a Fire OS 6.7 TV (`MIN_SDK=25`, `./launcher/install.sh --build`). On top of the
upstream home screen:

- **Favorites dock**: a row above All apps with any number of apps. The row ends with an
  **Edit favorites** tile that opens the app grid. Hold OK or ☰ on a tile:
  Add to / Remove from Favorites, Move. Options › Favorites is a checklist. Saved as
  `favorites=` in `home.conf`.
- **Background › App color** (default): the screen glows in the color of the app
  you are on (YouTube red, Spotify green), taken from its banner and faded in.
- **Getting more apps**: All apps ends with **Get more apps** (opens the Appstore's Featured
  page) and **App library**. Favorites and App library open a full-screen grid of every
  installed app: OK ticks an app in or out (a number shows its place in Favorites, a check
  that it is in All apps), Back closes. Both are also in Options › Home screen.
- **All apps layout** (Options › Home screen): **Grid** (default; every app at once, lines
  that wrap, the page scrolls down) or **Scroll** (one row that scrolls sideways).
- **Recently used** row (Options › Home screen › Home screen layout, on by default): the last 5 apps opened, from
  UsageStats (the Fire OS 6 Keeper's permission), newest first.
- **"Switch input?" card**: when a device on an HDMI input turns on while the home screen is
  showing, a card offers **Switch** / **Not now** (gone after 15 s). Options › Home screen ›
  Detect input. Test it with `adb shell touch /sdcard/Android/data/tv.dumbtv.home/files/test-input`
  and return to the home screen.
- **Night mode** (Options › Night): a schedule with exact start/end times (time picker)
  and a dim slider (10-90 %); warm dim, no screen saver. The top bar's moon-and-stars button
  switches it on or off now; that lasts until the schedule's next change.
- **Sleep timer** (top bar, stopwatch icon): 15 min to 2 h, or **Custom…** (hours and minutes,
  up to 9 h 55), then the TV turns off. While it runs the button stays open with the time left,
  and its menu shows the time left with **Add 15 / 30 minutes**, **New length…** and
  **Cancel the timer** (no "Off" row when nothing is running). It needs dumbtv as a device administrator
  with the single "lock screen" policy (what turns a TV off). Fire OS 6 answers the normal
  "Activate" screen with a dummy, so allow it once from a computer:
  `adb shell dpm set-active-admin tv.dumbtv.home/.Admin`. Without it the button offers Fire TV's
  own sleep timer. Options › More › Remove sleep timer permission, or `install.sh --undo`, takes
  it away (Android needs that before an uninstall).
- **Settings** (top bar, gear): a short menu of Fire TV sections that open directly (Wi-Fi &
  network, Device & Software, Apps, Remotes & Bluetooth) and **All Fire TV settings** for
  everything else. Home closes any open dropdown. Amazon's
  settings grid draws its top row (Device & Software, Network, Account) off screen on this TV,
  most likely because it counts on its slide-in animation and animations are off. Fire OS lets
  other apps open only these sections (the rest need an Amazon-only permission).
- **Screen care (OLED)** (Options › Screen saver, on by default): the home screen drifts up to
  2 dp to one of nine spots every 3 minutes, and after 5-30 min with no button pressed (Black screen after; default
  10) it fades to near black (any button brings it back; the TV still sleeps on its own schedule). The
  screen saver's clock and weather drift the same way and are drawn at 85 % brightness, and when the screen saver's time is up the screen goes dark instead of holding the last picture. OLED owners
  should also keep Fire TV's own sleep setting on (not "Never").
- **Options › Help**: green/amber checks for the sleep timer permission, internet and weather
  (OK on an amber one explains the fix), a Home button row that only appears when Home opens
  Amazon's home (on Fire OS 6 it offers the usage-access setting), and a remote-button guide.
- **Home screen layout** (Options › Home screen): Favorites, Recently used, All apps and Inputs in
  any order (OK picks a row up to reorder, ▲▼ moves it, OK puts it down); ◀▶ shows or hides a row.
  All apps always shows, so every app stays reachable. The page lands on the top row.
- **Pop-ups** (app menu, rename, ZIP code, inputs, confirmations, help) all use one style
  (`Popup.java` on `Panel.java`): the dark card, accent-focused rows, pill buttons. Rows with a
  value (layout, visible inputs, overlay) are all `ValueRow`: ‹ › while focused, a Hidden row's
  name dimmed.
- **Key hints**: the app grid, Options and Move mode show which remote keys do what.
- **Mac tools** (`launcher/tools/`): `push-photos.sh <folder>` resizes photos to 1080p and sends
  them to the screen saver (`--replace`, `--list`, `--clear`); `settings.sh backup|restore|list`
  keeps copies of home.conf in `launcher/tools/backups/` (git-ignored).
- Fades (screen saver, background glow, the input card) are drawn frame by frame, so they stay
  smooth with Fire OS animations turned off.
- **Row title size** (Options › Home screen) scales FAVORITES / ALL APPS / INPUTS and their icons.
- **Apple TV-style look**: greeting, large clock and date, the focused app's name large above
  the rows (App names › At top), rounded tiles that lift with a shadow on focus.
- **Top bar** of icon buttons; the selected one slides open to show its name: Inputs dropdown (green dot = device plugged in), Wi-Fi name with a
  colored Wi-Fi icon (green online, amber no internet, red offline), Options, Settings.
  Amazon's home screen stays in Options › More on purpose, away from accidental presses.
- **Options**, grouped on the left (Right opens a group, Back or Left returns to the list): Home
  screen, Appearance, Weather, Screen saver, Night, More, Help, each heading over a rule. Values are one
  or two words (App names: At top / Under tile / Off). Row titles are larger with drawn icons. Rows that depend on
  another setting only appear when it is on.
- **Weather** (off by default): Options › Weather, Temperature, Weather city (enter a ZIP code;
  when other countries share the digits, pick the right place from a list). Uses
  [Open-Meteo](https://open-meteo.com) (no account, no key; only the city's coordinates are
  sent). Shows temperature, conditions and high/low by the clock; the screen saver adds one
  heads-up line ("Rain likely around 5 PM, take a jacket"). Each extra can be shown or hidden: condition
  icon, next 5 hours (icon, temperature, chance of rain), feels like & wind, next
  sunrise/sunset & tomorrow.
- **Screen saver** (off by default): after 1–10 idle minutes on the home screen, photos
  cross-fade under the clock and the weather (icon, temperature, conditions). Options › Screen
  saver › Adjust overlay puts each in any corner, or hides it; two in one corner stack. Any key
  returns. Runs 15 min–2 h, then the TV may
  sleep. Put your own pictures in `photos/` next to `home.conf`; until then 12 bundled
  public-domain landscapes are used (`assets/photos/CREDITS.txt`).

```sh
adb shell mkdir -p /sdcard/Android/data/tv.dumbtv.home/files/photos
adb push my-photos/*.jpg /sdcard/Android/data/tv.dumbtv.home/files/photos/
```

Permissions added: `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` (top bar), `INTERNET`
(weather only; nothing connects while weather is off).

## Using it

| Remote | Does |
|---|---|
| OK | open the app, or switch to the input |
| Hold OK, or ☰ Menu | Move, Rename, Hide, App info |
| Move mode | ◀ ▶ moves the tile, any other key drops it |
| Up from the top row | Options and Settings |
| Back on the home screen | jump to the first tile |
| Hold HOME | Fire OS quick menu (Amazon's, unchanged) |

**Options** cycles each setting with ◀ ▶ or OK: accent color, tile size, app names
(on focus / always / never), clock format, inputs row, background (black, graphite,
midnight, or an image), image dim and wallpaper rotation, and **Next wallpaper**
steps to the next image. It also lists which apps and inputs appear on
the home screen. Apps without a TV banner, like the Appstore or Downloads, start
hidden; tick them there to add them.

## The settings file

Everything is stored in one plain text file on the TV:

```
/sdcard/Android/data/tv.dumbtv.home/files/home.conf
```

```
# white blue cyan green amber red pink violet, or #RRGGBB
accent=blue
# small medium large
tiles=medium
# focus always never
labels=focus
# auto 24h 12h
clock=auto
# on off
inputs=on
# black graphite midnight image
background=black
# 0 20 40 60 80, image background only
dim=40
# off visit hourly daily daytime (visit = every return to the home screen)
rotate=off
apps.order=com.amazon.firetv.youtube,org.videolan.vlc
apps.hide=org.videolan.vlc
apps.show=com.amazon.venezia
inputs.hide=com.droidlogic.tvinput/.services.AV1InputService/HW1
name.com.droidlogic.tvinput/.services.Hdmi2InputService/HW6=PS5
```

Comments go on their own line; anything after `=` is the value.

Back it up with `adb pull`, restore it with `adb push`. Hand edits apply the next
time the home screen comes back. For a wallpaper, push `wallpaper.jpg` (or `.png`)
into the same folder and set `background=image`. For several, push them into a
`wallpapers/` subfolder instead (`.jpg`, `.png` or `.webp`, shown in name order) and
pick a rotation; while that folder has images, `wallpaper.jpg` is ignored.

```sh
adb shell mkdir -p /sdcard/Android/data/tv.dumbtv.home/files/wallpapers
adb push *.jpg /sdcard/Android/data/tv.dumbtv.home/files/wallpapers/
```

`rotate=daytime` matches wallpapers to the time of day: name a file with `morning` or `day`
(06:00–13:00), `afternoon` or `evening` (13:00–20:00), or `night` (the rest), for example
`morning-coast.jpg` or `night-city.jpg`. Several files for one period take turns on each
return home. With no file named that way, the period simply picks one image of the folder.

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

## Files

| File | What it is |
|---|---|
| `src/.../Home.java` | the home screen: clock, Apps row, Inputs row, tile menu, move mode |
| `src/.../Tile.java` | one tile, drawn in a single `onDraw`: banner or icon, focus ring |
| `src/.../Catalog.java` | which apps and inputs exist, and which are shown in what order |
| `src/.../Config.java` | the `home.conf` reader and writer |
| `src/.../Options.java` | the settings screen |
| `src/.../Guard.java` | the Fire OS 7/8 HOME takeover described above |
| `src/.../Keeper.java` | the Fire OS 6 HOME takeover (UsageStats + moveTaskToFront) |
| `src/.../BootReceiver.java` | restarts the Keeper after a boot or an update |
| `src/.../Ui.java` | shared look: color tokens (GOOD, WARN, ACTIVE, PANEL, MENU, RULE), text, pills, dots, rules, focus backgrounds, notices |
| `src/.../Dropdown.java` | the top-bar dropdowns (Inputs, Sleep timer, Settings) |
| `src/.../Panel.java` | the centered card every custom dialog uses |
| `src/.../Popup.java` | every pop-up on that card: message, pick-one list, checklist, choices, text/ZIP entry |
| `src/.../ValueRow.java` | the one name-and-value row every pop-up list uses |
| `src/.../FireSettings.java` | the Fire TV settings screens other apps may open, and the full grid |
| `src/.../ScreenCare.java` | OLED care: drift and go dark when idle |
| `src/.../Help.java`, `RowsEditor.java`, `TimeChooser.java` | Help checks and remote guide, row order, time picker |
| `sdk.sh` | finds the Android SDK and a JDK; shared by build.sh and test.sh |
| `test.sh`, `test/` | plain-JVM tests for pure logic (labels, row order, time steps) |
| `build.sh` | aapt2 → javac → d8 → zipalign → apksigner |
| `install.sh` | download + checksum (or `--build`), install, HOME hook (Guard or Keeper), `--undo` |
| `version.env` | the only place `VERSION` and `APK_SHA256` live; both scripts read it |
| `release-check.sh` | proves version, built APK and GitHub release agree (`--local`, `--remote`, `--manifest`) |

## Releasing

1. Edit `version.env` (bump `VERSION`, `VERSION_CODE`, `VERSION_NAME`).
2. `./build.sh`, then put the new SHA-256 in `version.env`.
3. Upload `build/dumbtv-home.apk` to a release tagged `launcher-v<VERSION>`.
4. `./launcher/release-check.sh` — it fails if the tag, the APK and the hash
   disagree. CI runs `--manifest` on every push.
