#!/bin/sh
# SPDX-License-Identifier: Apache-2.0

set -eu

repository_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
report_dir=$(mktemp -d)
trap 'rm -rf "$report_dir"' EXIT HUP INT TERM

cd "$repository_dir"

./gradlew :app:dependencies \
    --configuration debugRuntimeClasspath \
    --no-daemon \
    --console=plain > "$report_dir/debug-runtime.txt"
./gradlew :app:dependencies \
    --configuration debugAndroidTestRuntimeClasspath \
    --no-daemon \
    --console=plain > "$report_dir/android-test-runtime.txt"

require_coordinate() {
    coordinate=$1
    report=$2
    if ! grep -Fq -- "$coordinate" "$report"; then
        echo "Missing resolved dependency: $coordinate" >&2
        return 1
    fi
}

forbidden_pattern='com\.android\.support([.:])|androidx\.appcompat:|androidx\.test\.espresso:'
if grep -E "$forbidden_pattern" "$report_dir/debug-runtime.txt" \
        "$report_dir/android-test-runtime.txt"; then
    echo "Forbidden legacy or unused dependency resolved" >&2
    exit 1
fi

require_coordinate "androidx.core:core:1.19.0" "$report_dir/debug-runtime.txt"
require_coordinate "androidx.test:runner:1.7.0" "$report_dir/android-test-runtime.txt"
require_coordinate "androidx.test.ext:junit:1.3.0" "$report_dir/android-test-runtime.txt"

echo "Resolved AndroidX dependencies verified: Core 1.19.0; runner 1.7.0; JUnit 1.3.0"
