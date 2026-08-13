#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

readonly expected_application_id="io.github.baodeep.hackerskeyboard"
readonly legacy_application_id="org.pocketworkstation.pckeyboard"

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 APK [APK ...]" >&2
  exit 2
fi

if ! command -v apkanalyzer >/dev/null 2>&1; then
  echo "apkanalyzer is required to verify APK manifests" >&2
  exit 1
fi

for apk in "$@"; do
  if [[ ! -f "${apk}" ]]; then
    echo "APK not found: ${apk}" >&2
    exit 1
  fi

  actual_application_id="$(apkanalyzer manifest application-id "${apk}")"
  actual_application_id="${actual_application_id//$'\r'/}"
  if [[ "${actual_application_id}" != "${expected_application_id}" ]]; then
    echo "Unexpected application ID in ${apk}: ${actual_application_id}" >&2
    exit 1
  fi

  if apkanalyzer manifest print "${apk}" | grep --fixed-strings "${legacy_application_id}" >/dev/null; then
    echo "Legacy application identity remains in merged manifest: ${apk}" >&2
    exit 1
  fi

  echo "Verified application ID ${expected_application_id} in ${apk}"
done
