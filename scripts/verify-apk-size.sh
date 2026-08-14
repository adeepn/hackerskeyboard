#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 APK MAX_BYTES" >&2
  exit 2
fi

readonly apk="$1"
readonly max_bytes="$2"

if [[ ! -f "${apk}" ]]; then
  echo "APK not found: ${apk}" >&2
  exit 1
fi

if [[ ! "${max_bytes}" =~ ^[1-9][0-9]*$ ]]; then
  echo "MAX_BYTES must be a positive integer: ${max_bytes}" >&2
  exit 2
fi

actual_bytes="$(wc -c < "${apk}" | tr -d '[:space:]')"
if (( actual_bytes > max_bytes )); then
  echo "APK size ${actual_bytes} exceeds limit ${max_bytes}: ${apk}" >&2
  exit 1
fi

echo "Verified APK size ${actual_bytes}/${max_bytes} bytes: ${apk}"
