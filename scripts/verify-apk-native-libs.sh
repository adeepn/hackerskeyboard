#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

readonly native_library="libjni_pckeyboard.so"
readonly expected_abis=(
  "armeabi-v7a"
  "arm64-v8a"
  "x86"
  "x86_64"
)

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 APK [APK ...]" >&2
  exit 2
fi

for apk in "$@"; do
  if [[ ! -f "${apk}" ]]; then
    echo "APK not found: ${apk}" >&2
    exit 1
  fi

  for abi in "${expected_abis[@]}"; do
    entry="lib/${abi}/${native_library}"
    if ! unzip -Z1 "${apk}" | grep --fixed-strings --line-regexp --quiet "${entry}"; then
      echo "Missing ${entry} in ${apk}" >&2
      exit 1
    fi
  done

  echo "Verified native ABIs in ${apk}"
done
