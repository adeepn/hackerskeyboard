# Privacy hardening and policy alignment

Owner decision: 27 November 2026. No advertising, AD_ID, analytics or embedded
crash SDK. Begin crash/ANR monitoring with Google Play Android vitals.

## Required behavior

- IME diagnostics must not print typed, composing, surrounding or extracted
  text, dictionary entries or other editor content. Audit superclass dump
  output as well as the explicit mComposing line; retain safe state only.
- Respect IME_FLAG_NO_PERSONALIZED_LEARNING. Recognized text/visible/web and
  numeric password editors must not write learned words/bigrams or promote
  them into the system dictionary. Reset policy at editor/session transitions
  and guard actual persistence paths, not only candidate visibility.
- Sensitive editors must not expose or dispatch voice input. Guard direct
  dispatch as well as the microphone UI; preserve ordinary input/voice routes.
- Add a user-triggered privacy-policy entry point using the existing external
  browser intent pattern. Add no network permission or WebView. Preserve
  settings keys and application/signing identity.
- Published policy must accurately distinguish local learning, automatic word
  promotion, editor delivery, external voice providers, developer telemetry,
  OS backup/restore and deletion. Do not promise more than the verified backup
  implementation; do not silently disable backup.
- Link the exact product name Hacker's keyboard: Rebirth and retain the policy
  URL https://baodeep.com/hkkpolicy. The display-name PR remains separate.

## Non-goals and limits

No advertising or crash SDK; no telemetry transport, cloud speech rewrite,
full keyboard refactor or settings migration. No real user text, logcat dumps
or private dictionaries in tests. Use synthetic sentinels and disposable CI
installs. A legacy speech bridge limitation must be reported if its API cannot
safely cancel deferred results; never invent a JAR API or enable dormant parts.

## Acceptance

Tests demonstrate redaction and no-learning/password/ordinary-editor behavior,
including editor transitions and actual write/dispatch boundaries. Resource
compilation, settings/identity guards, lint and API24/36-16KB/37 smoke remain
mandatory. Owner approval is required for merge; website changes are prepared
against the owning source and publication status must be explicit.

## Accepted voice tradeoff

[DECISION] After reviewing the missing cancellation API, the owner explicitly
approved removal of the legacy JAR fallback on all Android versions. Preserve
voice-IME switching and report unavailable when no provider exists. A replacement
recognizer bridge is deferred to #93 and must bind/cancel results by session.
