#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

./gradlew :app:bundleRelease --no-daemon --stacktrace
python3 scripts/verify-release-bundle.py app/build/outputs/bundle/release/app-release.aab
