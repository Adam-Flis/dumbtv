# dumbtv home: a calmer home screen for Fire TV

This replaces Amazon's home screen on a Fire TV with a simple one: your apps, your HDMI inputs,
the time and the weather. No ads, no "recommended for you" rows, no tracking.

What you get:

- **Your apps and inputs only.** A Favorites row, Recently used, All apps and Inputs, in any order.
- **A sleep timer** right on the home screen: 15 minutes to 2 hours, or any length.
- **Night mode:** a warm dim in the evening, on a schedule or with one button.
- **A screen saver** with photos, the clock and the weather. It also comes on when an app sits
  paused, then lets the TV go to sleep.
- **Burn-in care for OLED TVs:** nothing stays still on screen for long.
- **Weather** (optional): now, the next hours and tomorrow, from your ZIP code.

It's a fork of [humblemuzzu/dumbtv](https://github.com/humblemuzzu/dumbtv), built and tested on a
Toshiba Fire TV Edition television (Fire OS 6.7). Setting it up takes about 30 minutes, a
computer and a few copy-and-paste commands. Nothing on the TV is permanently changed: one command
puts Amazon's home screen back.

> Using an AI assistant (Claude, ChatGPT, Copilot) to set this up? Point it at
> [AGENTS.md](AGENTS.md). This README is for doing it yourself.

## Will it work on my TV?

On the TV, open **Settings › My Fire TV › About** (on some models **Settings › Device & Software
› About**) and look at the software version.

| Your TV | Works? |
|---|---|
| Fire TV Edition TV or Fire TV stick with **Fire OS 6** | Yes. This is what it was built and tested on. |
| Fire TV with **Fire OS 7** | Should work. Upstream's home screen was built on Fire OS 7, but this fork's extras haven't been tested there. |
| Fire TV with **Fire OS 8** | No. Fire OS 8 won't hand over the Home button. |
| Google TV, Android TV, Samsung, LG, Roku, Vizio and others | No. See [docs/OTHER-TVS.md](docs/OTHER-TVS.md) for what is possible on those. |

## What you need

- **A computer** on the same Wi-Fi as the TV: a Mac, a Linux PC, or Windows with
  [WSL](https://learn.microsoft.com/windows/wsl/install) (then follow the Linux steps inside WSL).
- **The TV remote**, to switch on one developer setting.
- **About 30 minutes.** Most of it is installing the tools in step 2, which you only do once.

## Set it up, step by step

Commands go in the **Terminal** app on a Mac (or your Linux/WSL terminal). Copy each line,
paste it and press Return. Lines starting with `#` are notes; you don't need to type them.

### 1. Get this project onto your computer

```sh
git clone -b my-launcher https://github.com/Adam-Flis/dumbtv.git
cd dumbtv
```

No `git`? On the project's GitHub page, switch the branch menu (above the file list) to
**my-launcher**, then choose **Code › Download ZIP**. Unzip it, type `cd ` (with a space) in
Terminal, drag the unzipped folder onto the window and press Return.

Every command below runs from inside this `dumbtv` folder.

### 2. Install the tools (once)

The home screen is built on your computer from the source code in this folder, so it needs
Android's build tools and Java.

**Mac** (with [Homebrew](https://brew.sh); install that first if `brew` isn't found):

```sh
brew install android-platform-tools openjdk@17
brew install --cask android-commandlinetools
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
yes | sdkmanager --licenses
sdkmanager "platforms;android-25" "platforms;android-28" "build-tools;34.0.0"
```

**Linux / WSL** (Ubuntu or Debian):

```sh
sudo apt install adb openjdk-17-jdk unzip
# Android command-line tools: download "Command line tools only" for Linux from
# https://developer.android.com/studio#command-line-tools-only, then:
mkdir -p ~/android-sdk/cmdline-tools && unzip commandlinetools-linux-*.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
export ANDROID_HOME=~/android-sdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-25" "platforms;android-28" "build-tools;34.0.0"
```

On Linux, run `export ANDROID_HOME=~/android-sdk` again in any new terminal before step 6.

Check it worked: `adb version` and `java -version` should both print a version.

### 3. Turn on ADB debugging on the TV

ADB is how the computer talks to the TV. It is off until you switch it on.

1. On the TV: **Settings › My Fire TV › About**.
2. Select the TV's name (the first line) and press OK **7 times**. A message says you're now a
   developer.
3. Press Back once. **Developer options** is now in the My Fire TV menu. Open it and turn on
   **ADB debugging**.

### 4. Find the TV's IP address

On the TV: **Settings › My Fire TV › About › Network**. Write down the **IP Address**, which looks
like `192.168.1.23`.

### 5. Connect the computer to the TV

Put your TV's address in place of `192.168.1.23`:

```sh
adb connect 192.168.1.23:5555
```

The TV shows **"Allow USB debugging?"**. Tick **Always allow from this computer** and choose
**OK**. Then check:

```sh
adb devices
```

The list should end in `device`. If it says `unauthorized`, look at the TV and accept the
prompt, then run `adb devices` again.

### 6. Install the home screen

```sh
./launcher/install.sh
```

It builds the app, installs it, makes the Home button open it, and allows the sleep timer and
the screen saver in every app. The first run takes a minute or two. It ends like this (on Fire OS
6):

```
installed tv.dumbtv.home
sleep timer: allowed
screen saver in apps: allowed
home: dumbtv (Keeper, no accessibility; sdk 25)
```

The first run also says `created signing key .../launcher/release.keystore`. **Keep a copy of
that file** somewhere safe: every future update has to be signed with it.

If it stops with an `error:` line instead, see [If something goes wrong](#if-something-goes-wrong).

### 7. Check it

Press **Home** on the remote. The new home screen should appear within a second.

### 8. Make it yours, on the TV

All of this is in **Options** (the sliders button at the top of the home screen):

- **Favorites:** go to the end of the Favorites row and choose **Edit favorites**.
- **Weather:** Options › Weather. Turn it on and enter your ZIP code.
- **Screen saver:** Options › Screen saver. Pick when it starts (1 to 15 minutes) and **Sleep
  after saver** (how long it runs before the TV turns off). **Adjust overlay** moves the clock and
  weather to any corner.
- **Night mode:** Options › Night. Set the times and how dim.
- **Options › Help:** every item should show a green dot. An amber one says what to do.

### 9. Optional: your own photos, and a backup

The screen saver shows 12 built-in landscape photos until you add your own:

```sh
./launcher/tools/push-photos.sh ~/Pictures/TV
```

This resizes the photos on your computer and sends them to the TV. `--list` shows what's there and
`--clear` goes back to the built-in ones.

To save your settings (favorites, order, names, options) to the computer:

```sh
./launcher/tools/settings.sh backup
```

`./launcher/tools/settings.sh restore` puts the newest backup back.

### 10. Turn ADB debugging off again

On the TV: **Settings › My Fire TV › Developer options › ADB debugging › Off**. That stops
anyone else on your network from controlling the TV. Turn it back on only to update.

## Using it day to day

[launcher/GETTING-STARTED.md](launcher/GETTING-STARTED.md) explains the screen and the remote
for everyone who watches the TV. Print it or send it to them. Lost? **Press Home.**

## Updating

Turn ADB debugging on, then:

```sh
cd dumbtv
git pull
adb connect 192.168.1.23:5555
./launcher/install.sh
```

Settings, favorites and photos stay. Use the same computer (or the same `release.keystore`): Android
refuses an update signed with a different key.

## Undo

```sh
./launcher/install.sh --undo
```

Home goes back to Amazon's home screen, Amazon's screensaver comes back, and the sleep timer
permission is removed. The app stays installed but unused. To remove it completely afterwards:

```sh
adb uninstall tv.dumbtv.home
```

## If something goes wrong

| What you see | What to do |
|---|---|
| `adb devices` says `unauthorized` | Look at the TV, accept "Allow USB debugging?" and tick Always allow. |
| `error: no TV connected` | Run `adb connect <ip>:5555` again. Check that ADB debugging is still on, the TV and computer are on the same Wi-Fi, and the IP address hasn't changed (step 4). |
| `android.jar missing` or `no build-tools` | The Android tools aren't installed or can't be found: repeat step 2. On Linux, `export ANDROID_HOME=~/android-sdk` first. |
| `no JDK found` | Repeat step 2 (Java 17). |
| `signed with a different key` | The TV has a copy built on another computer. The error lists three commands: back up the settings, uninstall, run `install.sh` again. |
| Home opens Amazon's home screen | Run `./launcher/install.sh` again. Right after the TV starts, it can take a few seconds the first time. |
| Options › Help says Sleep timer or Screen saver in apps "Needs setup" | Run `./launcher/install.sh` again with ADB debugging on. |
| No weather | Options › Weather: turn it on and enter a ZIP code. Options › Help › Internet should be green. |
| You need Amazon's own home screen once (to rent a movie, say) | Options › More › Amazon home screen. Press Home to come back. |

## What it changes on the TV

Everything here is undone by `install.sh --undo` (except the two idle timers, which stay at
their last values; set them in Fire TV's own settings if you like):

- **The Home button:** on Fire OS 6 a small background service (with "usage access") brings this
  home screen back whenever Amazon's appears. On Fire OS 7 it's an accessibility service and one
  alarm setting.
- **Sleep timer:** the app is a device administrator with one power only, "lock the screen",
  which is how an app turns a TV off.
- **Screen saver:** the app may change secure settings, so it can make itself Fire TV's
  screensaver and set when the screen saver starts and when the TV sleeps.

## Privacy

The home screen shows no ads, has no account and collects nothing. It only goes online for the
weather: it sends your town's coordinates to [Open-Meteo](https://open-meteo.com), which needs
no account or key. With weather off it never connects to anything.

## More

- [AGENTS.md](AGENTS.md): the same setup for an AI assistant, plus how the code is organized.
- [launcher/README.md](launcher/README.md): how it works inside, the settings file, building
  and testing.
- [docs/DEBLOAT.md](docs/DEBLOAT.md): upstream's separate debloat tool. **Not needed for the home
  screen, and not safe on a Fire OS 6 television** (it disables the Live TV and HDMI inputs).

MIT licence ([LICENSE](LICENSE)). The built-in photos are public domain
([credits](launcher/assets/photos/CREDITS.txt)). Nothing here is Amazon code.
