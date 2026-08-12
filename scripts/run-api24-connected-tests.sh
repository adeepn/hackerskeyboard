#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

android_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${android_sdk_root}" ]]; then
  echo "ANDROID_SDK_ROOT or ANDROID_HOME must point to an Android SDK" >&2
  exit 1
fi

avd_name="hackers-keyboard-api24"
system_image="system-images;android-24;default;x86"
emulator_log="$(mktemp)"
emulator_pid=""

cleanup() {
  if [[ -n "${emulator_pid}" ]] && kill -0 "${emulator_pid}" 2>/dev/null; then
    kill "${emulator_pid}" 2>/dev/null || true
    wait "${emulator_pid}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

sdkmanager "emulator" "${system_image}"
avdmanager_status=0
avdmanager create avd \
  --force \
  --name "${avd_name}" \
  --package "${system_image}" \
  --device "pixel" \
  <<< "no" || avdmanager_status="$?"

if ! avdmanager list avd | grep -Fq "Name: ${avd_name}"; then
  echo "Android API 24 AVD creation failed with status ${avdmanager_status}" >&2
  exit 1
fi

"${android_sdk_root}/emulator/emulator" \
  -avd "${avd_name}" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect \
  >"${emulator_log}" 2>&1 &
emulator_pid="$!"

boot_completed=""
for _attempt in $(seq 1 120); do
  if ! kill -0 "${emulator_pid}" 2>/dev/null; then
    echo "Android API 24 emulator exited before boot completed" >&2
    tail -200 "${emulator_log}" >&2
    exit 1
  fi
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [[ "${boot_completed}" == "1" ]]; then
    break
  fi
  sleep 2
done

if [[ "${boot_completed}" != "1" ]]; then
  echo "Android API 24 emulator did not boot within 240 seconds" >&2
  tail -200 "${emulator_log}" >&2
  exit 1
fi

adb shell input keyevent 82
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
