# Privacy behavior and release acceptance

[DECISION] Keep Hacker's keyboard: Rebirth free of advertising, AD_ID, analytics,
network permissions and embedded crash SDKs. Begin crash/ANR investigation with
Google Play Android vitals. The display-name change is separate from this work.

## Implementation and publication boundaries

The setup activity provides a privacy-policy button using user-triggered
`ACTION_VIEW` for `https://baodeep.com/hkkpolicy`. It adds no editor extras,
WebView, preference keys or network permission. An unavailable browser produces a
localized message containing the URL. Default and Russian labels are provided;
other locales use the default resource.

The [policy draft](privacy-policy.md) describes verified storage and external
boundaries without promising universal device-only storage or backup exclusion.
It is prepared text, not evidence that the website has changed. Audit evidence is recorded with the privacy change. The published policy's
absolute device-only/no-other-recipient claims require replacement through the
website's owning process.

[GOTCHA] Current distributed releases must not be assumed to have this branch's
hardening. The privacyfix work targets content-free service diagnostics, password
and no-personalized-learning write guards, and sensitive-editor voice guards.
Verify the final artifact and release before describing these fixes as shipped.
The draft deliberately does not promise that passwords are never accessed: an
IME necessarily receives input to deliver it to the editor.

## Android vitals acceptance

The owner must inspect Play Console for the actual distributed version and track.
No new telemetry transport, SDK, account identifier or custom event stream is
introduced. Record version codes, observation window and crash/ANR categories;
small test audiences may not produce useful reports. Do not claim Console access
or enabled reports based on source inspection. Treat diagnostic details as
sensitive; do not paste raw reports, user text, memory dumps or dictionaries into
issues or model prompts. Use sanitized technical reproductions with synthetic text.

## Release checks

- Run `PrivacyPolicyEntryTest`: no browser navigation on setup launch; click opens
  only the exact URL with no extras/clip data; no-browser failure keeps setup alive.
- Inspect default/Russian setup on the API 24, API 36 16 KB and API 37 matrix,
  including font scaling, narrow screens and external browser return.
- Run core privacy tests for service dump redaction, ordinary/password/private
  editor transitions, actual dictionary write/promotion boundaries and voice routes.
- Inspect final APK/AAB permissions and dependencies for INTERNET, AD_ID and SDKs;
  rerun settings/application identity guards, resource builds, lint and full prek.
- [DEFER] Backup/restore and OEM device-transfer testing remains required before
  stronger learned-dictionary exclusion or deletion guarantees can be made.
- Confirm final policy text matches the shipped artifact and publish through the
  website owner; verify the live policy separately. Reconcile Play Data Safety
  against all distributed tracks and platform/SDK boundaries before submission.

No website publication, Console configuration or release is performed by adding
this document. Test command results belong in the PR and durable verification
artifacts; this checklist alone is not a passing result.
