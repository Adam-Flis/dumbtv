Read `AGENTS.md` first. It is the full contract: setting up dumbtv home on a Fire TV with
`./launcher/install.sh` (part 1), and working on the code in `launcher/` (part 2).

The short version of its rules: never run `scripts/all.sh` or `scripts/debloat.sh` (they break
Live TV and the HDMI inputs on a Fire OS 6 television); never touch `/system` or factory-reset;
ask before pressing remote keys; change TV settings only through `install.sh`; never delete or
commit `launcher/release.keystore`; keep IP addresses and Wi-Fi names out of commits.
