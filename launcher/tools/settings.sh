#!/usr/bin/env bash
# Backs up or restores the home screen settings (home.conf: favorites, order, names, options).
#   ./launcher/tools/settings.sh backup            -> launcher/tools/backups/home-YYYYmmdd-HHMM.conf
#   ./launcher/tools/settings.sh restore [file]    newest backup when no file is given
#   ./launcher/tools/settings.sh list
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
ADB=${ADB:-adb}
CONF=/sdcard/Android/data/tv.dumbtv.home/files/home.conf
DIR=${DUMBTV_BACKUPS:-$PWD/backups}
adb_() { "$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} "$@"; }
need_tv() { [[ $(adb_ get-state 2>/dev/null || true) == device ]] || { echo "error: no TV connected (adb connect <tv-ip>:5555)" >&2; exit 1; }; }

case ${1:-} in
  backup)
    need_tv; mkdir -p "$DIR"
    out="$DIR/home-$(date +%Y%m%d-%H%M).conf"
    adb_ shell cat "$CONF" > "$out"
    [[ -s $out ]] || { rm -f "$out"; echo "error: no settings on the TV yet" >&2; exit 1; }
    echo "saved $out" ;;
  restore)
    need_tv
    file=${2:-$(ls -t "$DIR"/home-*.conf 2>/dev/null | head -1)}
    [[ -f ${file:-} ]] || { echo "error: no backup found (run: $0 backup)" >&2; exit 1; }
    adb_ push "$file" "$CONF" >/dev/null
    # back to the home screen so it reads the file before it saves anything of its own
    adb_ shell input keyevent HOME
    echo "restored $file" ;;
  list) ls -t "$DIR"/home-*.conf 2>/dev/null || echo "no backups yet" ;;
  *) sed -n 2,5p "$0"; exit 2 ;;
esac
