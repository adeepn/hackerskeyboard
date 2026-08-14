#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

readonly expected_application_id="com.baodeep.hackerskeyboard"
readonly legacy_application_id="org.pocketworkstation.pckeyboard"
readonly gradle_properties="gradle.properties"

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 APK [APK ...]" >&2
  exit 2
fi

if ! command -v apkanalyzer >/dev/null 2>&1; then
  echo "apkanalyzer is required to verify APK manifests" >&2
  exit 1
fi

if [[ ! -f "${gradle_properties}" ]]; then
  echo "Version source not found: ${gradle_properties}" >&2
  exit 1
fi

expected_version_code="$(sed -n 's/^appVersionCode=//p' "${gradle_properties}")"
expected_version_name="$(sed -n 's/^appVersionName=//p' "${gradle_properties}")"
if [[ ! "${expected_version_code}" =~ ^[0-9]+$ || -z "${expected_version_name}" ]]; then
  echo "Invalid canonical version in ${gradle_properties}" >&2
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

  actual_version_code="$(apkanalyzer manifest version-code "${apk}")"
  actual_version_code="${actual_version_code//$'\r'/}"
  actual_version_name="$(apkanalyzer manifest version-name "${apk}")"
  actual_version_name="${actual_version_name//$'\r'/}"
  if [[ "${actual_version_code}" != "${expected_version_code}" ]]; then
    echo "Unexpected versionCode in ${apk}: ${actual_version_code}" >&2
    exit 1
  fi
  if [[ "${actual_version_name}" != "${expected_version_name}" ]]; then
    echo "Unexpected versionName in ${apk}: ${actual_version_name}" >&2
    exit 1
  fi

  if apkanalyzer manifest print "${apk}" | grep --fixed-strings "${legacy_application_id}" >/dev/null; then
    echo "Legacy application identity remains in merged manifest: ${apk}" >&2
    exit 1
  fi

  echo "Verified ${expected_application_id} ${expected_version_name} (${expected_version_code}) in ${apk}"
done
