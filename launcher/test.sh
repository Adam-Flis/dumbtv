#!/usr/bin/env bash
# Runs the plain-JVM tests in launcher/test (pure logic: labels, row order, time steps).
# Same SDK and JDK as build.sh; no TV, no emulator.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
MIN_SDK=${MIN_SDK:-25}
# shellcheck disable=SC1091
source ./sdk.sh

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
# -sourcepath compiles only the classes the tests reach, against android.jar's stubs
"$JAVA_HOME/bin/javac" --release 8 -Xlint:all -Xlint:-options -Werror -encoding UTF-8 \
  -classpath "$JAR" -sourcepath src -d "$OUT" test/tv/dumbtv/home/*.java
"$JAVA_HOME/bin/java" -cp "$OUT:$JAR" tv.dumbtv.home.LogicTest
