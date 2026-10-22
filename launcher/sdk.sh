# Sourced by build.sh and test.sh: finds the Android SDK for $MIN_SDK and a JDK.
# Sets SDK, JAR (android.jar), BT (newest build-tools), KEYSTORE, KEYPASS and exports JAVA_HOME;
# exits if the SDK or JDK is missing.
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z $SDK ]]; then
  for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk"; do
    if [[ -f $d/platforms/android-$MIN_SDK/android.jar ]]; then SDK=$d; break; fi
  done
fi
JAR=$SDK/platforms/android-$MIN_SDK/android.jar
BT=$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)
[[ -f $JAR ]] || { echo "error: $JAR missing — sdkmanager 'platforms;android-$MIN_SDK'" >&2; exit 1; }
[[ -x $BT/aapt2 ]] || { echo "error: no build-tools in $SDK — sdkmanager 'build-tools;34.0.0'" >&2; exit 1; }

if [[ -z ${JAVA_HOME:-} || ! -x ${JAVA_HOME:-}/bin/javac ]]; then
  for j in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
           /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home \
           "$(/usr/libexec/java_home 2>/dev/null || true)"; do
    if [[ -n $j && -x $j/bin/javac ]]; then JAVA_HOME=$j; break; fi
  done
fi
[[ -x ${JAVA_HOME:-}/bin/javac ]] || { echo "error: no JDK found — brew install openjdk@17" >&2; exit 1; }
export JAVA_HOME

# the signing key every APK from this folder shares (updates must keep the same key)
KEYSTORE=${DUMBTV_KEYSTORE:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/release.keystore}
KEYPASS=${DUMBTV_KEYPASS:-dumbtvhome}
