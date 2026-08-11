# ADR-0001: Android 7 minimum and modern native toolchain

- Status: accepted
- Date: 2026-08-11
- Decision owner: repository owner

## Context

Legacy v1 declared `minSdk 14`, but current Android NDK releases no longer
support that native platform. Keeping API 14 would require an obsolete NDK and
would conflict with current Android, security, toolchain and 16 KB page-size
requirements.

The owner confirmed that Android versions earlier than Android 7 are outside
the supported v2 product scope. Hacker's Keyboard includes a JNI dictionary
library, so the native toolchain is part of the compatibility contract.

## Decision

- v2 supports Android 7.0 and newer: `minSdk 24`;
- stable NDK r29 is pinned as `29.0.14206865`;
- CMake is pinned as `3.22.1`;
- native builds initially retain all four NDK r29 ABIs: `armeabi-v7a`,
  `arm64-v8a`, `x86`, and `x86_64`;
- no NDK minSdk suppression flag is allowed;
- native release artifacts must support 16 KB page sizes.

## Consequences

- Android 4.x, 5.x and 6.x devices cannot install v2;
- v1 remains the historical option for unsupported old devices;
- modern NDK and linker behavior can be used without pretending API 14
  compatibility;
- 32-bit ARM and x86 remain temporarily supported and may be reconsidered only
  through a separate compatibility ADR;
- ABI builds and 16 KB alignment require automated validation before release.

## Evidence

- Android NDK r29 is the current stable release and uses revision
  `29.0.14206865`;
- NDK r28 and newer produce 16 KB-aligned libraries by default;
- Google Play requires 16 KB page-size support for relevant Android 15+
  submissions since 1 November 2025.
