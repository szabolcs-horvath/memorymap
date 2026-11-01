#!/usr/bin/env bash
# Runs the UI tests on the local test emulator (MemoryMap_UI_Tests), and on no other device. See docs/ui-tests.md.
#
#   scripts/ui-tests.sh                           the whole suite
#   scripts/ui-tests.sh "MapTest|TimelineTest"    only the test classes whose names match
#
# It starts the emulator if it isn't running, waits until it has booted, and runs connectedDebugAndroidTest with
# ANDROID_SERIAL set to it: Gradle otherwise runs the tests on every connected device, and every test clears the debug
# app's data. (AGP's own --serial option doesn't work in AGP 9.4.1: it fails with an UnsupportedOperationException.)
# Afterwards it prints a table of the results. Works on macOS, Linux and Windows (Git Bash).
set -euo pipefail

AVD="${UI_TEST_AVD:-MemoryMap_UI_Tests}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESULTS="$ROOT/app/build/outputs/androidTest-results/connected"

fail() { echo "$*" >&2; exit 1; }

find_sdk() {
    local dir
    for dir in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        if [ -n "$dir" ] && [ -d "$dir" ]; then echo "$dir"; return; fi
    done
    if [ -f "$ROOT/local.properties" ]; then
        # local.properties escapes ':' and '\' (sdk.dir=C\:\\Users\\...).
        dir="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | sed 's/\\:/:/g; s/\\\\/\\/g' | tr -d '\r')"
        if [ -n "$dir" ] && command -v cygpath > /dev/null; then dir="$(cygpath -u "$dir")"; fi
        if [ -n "$dir" ] && [ -d "$dir" ]; then echo "$dir"; return; fi
    fi
    for dir in "${LOCALAPPDATA:-}/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -d "$dir" ]; then echo "$dir"; return; fi
    done
    fail "No Android SDK found. Set ANDROID_HOME."
}

# Gradle picks the JDK for its own work (gradle/gradle-daemon-jvm.properties); it only needs a Java to start with.
find_java_home() {
    if [ -n "${JAVA_HOME:-}" ]; then echo "$JAVA_HOME"; return; fi
    local dir
    for dir in "${LOCALAPPDATA:-}/Programs/Android Studio/jbr" "${PROGRAMFILES:-}/Android/Android Studio/jbr" \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" "$HOME/android-studio/jbr" \
        "/opt/android-studio/jbr"; do
        if [ -x "$dir/bin/java" ] || [ -x "$dir/bin/java.exe" ]; then echo "$dir"; return; fi
    done
    if command -v java > /dev/null; then return; fi
    fail "No Java found. Set JAVA_HOME, for example to Android Studio's 'jbr' folder."
}

SDK="$(find_sdk)"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"

# The serial (such as emulator-5554) of the running emulator named $AVD: `adb emu avd name` answers with its name.
find_serial() {
    local serial name
    for serial in $("$ADB" devices | tr -d '\r' | awk '$2 == "device" && $1 ~ /^emulator-/ { print $1 }'); do
        name="$("$ADB" -s "$serial" emu avd name 2> /dev/null | head -1 | tr -d '\r')"
        if [ "$name" = "$AVD" ]; then echo "$serial"; return; fi
    done
}

wait_for() {
    local what="$1"; shift
    local deadline=$((SECONDS + 180))
    until "$@"; do
        if [ "$SECONDS" -ge "$deadline" ]; then fail "Gave up waiting for $what after 3 minutes."; fi
        sleep 2
    done
}

serial="$(find_serial)"
if [ -z "$serial" ]; then
    "$EMULATOR" -list-avds | tr -d '\r' | grep -qx "$AVD" ||
        fail "There is no emulator named $AVD. Set it up as docs/ui-tests.md describes."
    echo "Starting the emulator $AVD..."
    nohup "$EMULATOR" -avd "$AVD" > /dev/null 2>&1 &
    disown
    started() { serial="$(find_serial)"; [ -n "$serial" ]; }
    wait_for "$AVD to start" started
fi
booted() { [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = "1" ]; }
wait_for "$AVD ($serial) to finish booting" booted

JAVA_HOME="$(find_java_home)"
if [ -n "$JAVA_HOME" ]; then export JAVA_HOME; JAVA="$JAVA_HOME/bin/java"; else JAVA="java"; fi

gradle_args=(connectedDebugAndroidTest)
if [ $# -gt 0 ]; then gradle_args+=("-Pandroid.testInstrumentationRunnerArguments.tests_regex=.*($1).*"); fi
rm -rf "$RESULTS"

echo "Running the UI tests on $AVD ($serial)..."
status=0
(cd "$ROOT" && ANDROID_SERIAL="$serial" ./gradlew "${gradle_args[@]}") || status=$?
"$JAVA" "$ROOT/.github/scripts/UiTestSummary.java" "$AVD" "$RESULTS" || true
exit "$status"
