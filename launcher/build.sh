#!/usr/bin/env bash
# Builds launcher/build/dumbtv-home.apk from the bare SDK tools: no Gradle, no libraries.
# Needs a JDK 17+ and an Android SDK with platforms;android-$MIN_SDK and any build-tools.
#   MIN_SDK=28 (default)  Fire OS 7/8, the published release
#   MIN_SDK=25            Fire OS 6 (Android 7.1). Compiling against android-25 makes javac
#                         reject any android.* API Fire OS 6 lacks; java.* calls are checked
#                         against JDK 8 by --release 8, so keep them to what Android 7.1 has.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# shellcheck disable=SC1091
source version.env
MIN_SDK=${MIN_SDK:-28}

# finds the SDK (JAR, BT) and a JDK (JAVA_HOME); shared with test.sh
# shellcheck disable=SC1091
source ./sdk.sh

# the logic tests first: a build that breaks them never reaches the TV (SKIP_TESTS=1 skips)
[[ ${SKIP_TESTS:-} == 1 ]] || MIN_SDK=$MIN_SDK ./test.sh

KEYSTORE=${DUMBTV_KEYSTORE:-$PWD/release.keystore}
KEYPASS=${DUMBTV_KEYPASS:-dumbtvhome}
if [[ ! -f $KEYSTORE ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -alias home -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KEYPASS" -keypass "$KEYPASS" -dname "CN=dumbtv home" >/dev/null
  echo "created signing key $KEYSTORE (keep it: updates must be signed with the same key)"
fi

# assets/ (the photo frame's default pictures) is packed as-is when present
ASSETS=
[[ -d assets ]] && ASSETS=assets
WORK=$(mktemp -d "${TMPDIR:-/tmp}/dumbtv-home.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" build

"$BT/aapt2" compile --dir res -o "$WORK/res.zip"
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml "$WORK/res.zip" \
  ${ASSETS:+-A "$ASSETS"} --min-sdk-version "$MIN_SDK" --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$WORK/gen" -o "$WORK/app.apk"

find src "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Xlint:-options -Werror -encoding UTF-8 \
  -classpath "$JAR" -d "$WORK/classes" @"$WORK/sources.txt"

find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$BT/d8" --release --min-api "$MIN_SDK" --lib "$JAR" --output "$WORK" @"$WORK/classes.txt"
(cd "$WORK" && zip -q -X app.apk classes.dex)

"$BT/zipalign" -f -p 4 "$WORK/app.apk" "$WORK/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEYPASS" --key-pass "pass:$KEYPASS" \
  --out build/dumbtv-home.apk "$WORK/aligned.apk"
"$BT/apksigner" verify build/dumbtv-home.apk

perms=$("$BT/aapt2" dump permissions build/dumbtv-home.apk | grep -c 'uses-permission' || true)
printf 'built build/dumbtv-home.apk  %s bytes  %s uses-permission entries\n' \
  "$(wc -c < build/dumbtv-home.apk | tr -d ' ')" "$perms"
