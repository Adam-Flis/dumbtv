#!/usr/bin/env bash
# Sends a folder of your photos to the TV for the photo frame (Options > Photo frame).
#   ./launcher/tools/push-photos.sh ~/Pictures/TV            add/refresh these photos
#   ./launcher/tools/push-photos.sh --replace ~/Pictures/TV  remove what is on the TV first
#   ./launcher/tools/push-photos.sh --list                   show what is on the TV
#   ./launcher/tools/push-photos.sh --clear                  remove all; the built-in photos come back
# Photos are resized on your Mac to fit 1920x1080 (JPEG), so the TV never decodes a 4000 px
# camera file. Needs adb (connected to the TV) and macOS `sips` (or ImageMagick on Linux).
set -euo pipefail
ADB=${ADB:-adb}
DEST=${DUMBTV_PHOTOS:-/sdcard/Android/data/tv.dumbtv.home/files/photos}
adb_() { "$ADB" ${ADB_SERIAL:+-s "$ADB_SERIAL"} "$@"; }

[[ $(adb_ get-state 2>/dev/null || true) == device ]] || { echo "error: no TV connected (adb connect <tv-ip>:5555)" >&2; exit 1; }

case ${1:-} in
  --list) adb_ shell "ls -l '$DEST' 2>/dev/null" | awk 'NF>=7{print $NF}'; exit 0 ;;
  --clear) adb_ shell "rm -rf '$DEST'"; echo "removed the photos from the TV; the built-in ones are used again"; exit 0 ;;
  --replace) replace=1; shift ;;
  ""|-h|--help) sed -n 2,9p "$0"; exit 0 ;;
  *) replace=0 ;;
esac
SRC=${1:?folder of photos}
[[ -d $SRC ]] || { echo "error: $SRC is not a folder" >&2; exit 1; }

WORK=$(mktemp -d "${TMPDIR:-/tmp}/dumbtv-photos.XXXXXX")
trap 'rm -rf "$WORK"' EXIT

n=0
while IFS= read -r -d '' f; do
  name=$(basename "$f"); base=${name%.*}
  out="$WORK/$(printf '%s' "$base" | tr -c 'A-Za-z0-9._-' '_').jpg"
  if command -v sips >/dev/null; then
    # -Z fits the long side; then cap the height so portrait shots stay inside 1080
    sips -s format jpeg -s formatOptions 85 -Z 1920 "$f" --out "$out" >/dev/null
    h=$(sips -g pixelHeight "$out" | awk '/pixelHeight/{print $2}')
    if [[ ${h:-0} -gt 1080 ]]; then sips --resampleHeight 1080 "$out" >/dev/null; fi
  elif command -v magick >/dev/null || command -v convert >/dev/null; then
    "$(command -v magick || command -v convert)" "$f" -auto-orient -resize '1920x1080>' -quality 85 "$out"
  else
    echo "error: needs macOS sips or ImageMagick" >&2; exit 1
  fi
  n=$((n + 1))
done < <(find "$SRC" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.heic' -o -iname '*.webp' \) -print0 | sort -z)

[[ $n -gt 0 ]] || { echo "error: no .jpg/.jpeg/.png/.heic/.webp photos in $SRC" >&2; exit 1; }
[[ $replace == 1 ]] && adb_ shell "rm -rf '$DEST'"
adb_ shell "mkdir -p '$DEST'"
adb_ push "$WORK"/. "$DEST"/ >/dev/null 2>&1 || { echo "error: could not copy to the TV" >&2; exit 1; }
echo "sent $n photo(s) to the TV ($(du -sh "$WORK" | awk '{print $1}')). They show from the next photo frame."
