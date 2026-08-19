#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"
EMULATOR="$SDK_ROOT/emulator/emulator"
AVD_NAME="${ANKAI_AVD_NAME:-qa_api28}"
EMULATOR_PORT="${ANKAI_EMULATOR_PORT:-5556}"
SERIAL="emulator-$EMULATOR_PORT"
PACKAGE="dev.claude.assistant"
ACTIVITY="$PACKAGE/.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
BOOT_TIMEOUT_SECONDS="${ANKAI_EMULATOR_BOOT_TIMEOUT:-180}"
STARTED_EMULATOR=0
EMULATOR_PID=""

if [[ -e /dev/kvm && ! -r /dev/kvm && "${ANKAI_KVM_REEXEC:-0}" != "1" ]]; then
    if getent group kvm | cut -d: -f4 | tr ',' '\n' | grep -Fxq "$USER"; then
        quoted_command="ANKAI_KVM_REEXEC=1"
        printf -v quoted_command '%s %q' "$quoted_command" "$0"
        for argument in "$@"; do
            printf -v quoted_command '%s %q' "$quoted_command" "$argument"
        done
        exec sg kvm -c "$quoted_command"
    fi
fi

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

cleanup() {
    if [[ "$STARTED_EMULATOR" == "1" ]]; then
        "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
        if [[ -n "$EMULATOR_PID" ]]; then
            wait "$EMULATOR_PID" 2>/dev/null || true
        fi
    fi
}
trap cleanup EXIT

[[ -x "$ADB" ]] || fail "adb not found at $ADB"
[[ -x "$EMULATOR" ]] || fail "emulator not found at $EMULATOR"
[[ ! -e /dev/kvm || -r /dev/kvm ]] || fail "current user cannot access /dev/kvm; activate membership in the kvm group"
"$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME" || fail "AVD '$AVD_NAME' is not installed"

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

./gradlew :app:assembleDebug --no-daemon
[[ -f "$APK" ]] || fail "debug APK was not created at $APK"

if ! "$ADB" devices | awk 'NR > 1 {print $1}' | grep -Fxq "$SERIAL"; then
    "$EMULATOR" \
        -avd "$AVD_NAME" \
        -port "$EMULATOR_PORT" \
        -no-window \
        -no-audio \
        -no-boot-anim \
        -no-snapshot-load \
        -gpu swiftshader_indirect \
        >"${TMPDIR:-/tmp}/ankai-emulator-$EMULATOR_PORT.log" 2>&1 &
    EMULATOR_PID=$!
    STARTED_EMULATOR=1
fi

deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
until "$ADB" -s "$SERIAL" get-state >/dev/null 2>&1; do
    if [[ "$STARTED_EMULATOR" == "1" ]] && ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
        fail "emulator exited before becoming available; see ${TMPDIR:-/tmp}/ankai-emulator-$EMULATOR_PORT.log"
    fi
    (( SECONDS < deadline )) || fail "emulator did not become available within $BOOT_TIMEOUT_SECONDS seconds"
    sleep 2
done
while [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
    if [[ "$STARTED_EMULATOR" == "1" ]] && ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
        fail "emulator exited before completing boot; see ${TMPDIR:-/tmp}/ankai-emulator-$EMULATOR_PORT.log"
    fi
    (( SECONDS < deadline )) || fail "emulator did not boot within $BOOT_TIMEOUT_SECONDS seconds"
    sleep 2
done

"$ADB" -s "$SERIAL" install -r -t "$APK" >/dev/null
"$ADB" -s "$SERIAL" shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell am force-stop "$PACKAGE"
"$ADB" -s "$SERIAL" shell am start -W -n "$ACTIVITY" >/dev/null

resumed="$($ADB -s "$SERIAL" shell dumpsys activity activities | grep -m1 'mResumedActivity' || true)"
[[ "$resumed" == *"$PACKAGE"* ]] || fail "launcher activity is not resumed: ${resumed:-no activity reported}"

pid="$($ADB -s "$SERIAL" shell pidof "$PACKAGE" | tr -d '\r')"
[[ -n "$pid" ]] || fail "app process is not running"

if "$ADB" -s "$SERIAL" logcat -d -b crash | grep -Fq "$PACKAGE"; then
    "$ADB" -s "$SERIAL" logcat -d -b crash >&2
    fail "crash log contains an entry for $PACKAGE"
fi

printf 'PASS: %s installed and launched on %s (%s), pid=%s\n' "$APK" "$AVD_NAME" "$SERIAL" "$pid"
