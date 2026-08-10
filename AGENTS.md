# Hacker's Keyboard v2 — contributor instructions

## Mission

Modernize Hacker's Keyboard for current Android while preserving its behavior as
a power-user IME. Work is based on branch `v2`. The migration plan and acceptance
criteria are documented in `docs/`.

## Required reading

Before changing production code, read:

- `docs/README.md`
- `docs/modernization-plan.md`
- `docs/architecture.md`
- `docs/testing-strategy.md`
- `docs/fork-research.md` when evaluating or importing fork code

## Working branch and change scope

- `v2` is the project's main integration branch and is intended to become the
  GitHub default branch.
- Do project work on a short-lived branch based on the latest `v2`.
- Open every development pull request against `v2`.
- Do not commit directly to `master`; it is the preserved v1 baseline.
- Keep migration changes small and reviewable. Do not combine a build-system
  migration, behavior refactor, resource reformat, and feature addition in one
  change.
- Preserve unrelated local changes. Never replace the repository wholesale with
  a fork.
- Do not change `applicationId` or preference keys without an explicit migration
  plan. Existing installations, dictionaries, and settings must remain usable.

## Compatibility principles

- Functional parity has priority over language or UI-framework modernization.
- Keep `InputMethodService`, the custom keyboard `View`/`Canvas` renderer, XML
  layouts, compose/dead-key data, and the native dictionary engine until tests
  justify replacing them.
- New isolated code may be Kotlin. Do not mechanically convert large Java files.
- Compose may be evaluated for standalone settings UI, but not introduced into
  the latency-sensitive keyboard surface without measurements and a design ADR.
- Keep C++/JNI during the initial Android upgrade. Test every native ABI and 16 KB
  page-size compatibility before release.
- Prefer narrow package visibility declarations (`<queries>`) over
  `QUERY_ALL_PACKAGES`.
- Internal broadcasts and pending intents must be explicit and least-privileged.
- Treat typed text, surrounding text, suggestions, dictionaries, and logs as
  sensitive data. Never add telemetry or network access without an explicit
  privacy review.

## Apache-2.0 and provenance

- The project is distributed under Apache License 2.0. Read
  `docs/licensing.md` before adding, replacing, moving, or importing source and
  resource files.
- Never remove or rewrite an existing copyright, patent, trademark, license, or
  attribution notice unless a documented legal/provenance review proves it no
  longer applies.
- Materially modified legacy files must retain their original header and carry
  a prominent notice that the file was changed for Hacker's Keyboard v2. Do not
  imply that an original author wrote later modifications.
- New copyright notices may name only the actual copyright owner. Never name an
  AI tool or model as author/copyright owner.
- Code imported or adapted from upstream/forks requires a compatible license,
  exact repository and commit provenance, preserved notices, and attribution in
  the PR. Unknown-license snippets and attachments must not be copied.
- Keep the root `LICENSE` unmodified. Maintain `NOTICE` and third-party license
  information when the licensing inventory determines they are required.
- Source and binary release artifacts must include the Apache-2.0 license and
  all applicable notices.

## Build and dependency policy

- Use the repository Gradle wrapper and the JDK version pinned by the project.
- Use `google()` and `mavenCentral()`; do not add `jcenter()` or unmaintained
  binary dependencies.
- Prefer AndroidX public APIs. Do not use hidden/non-SDK Android APIs.
- Pin AGP, Gradle, JDK, SDK, NDK, and CMake versions in tracked configuration.
- Release builds must not be debuggable. Do not suppress lint globally to make a
  migration pass; document and narrowly scope unavoidable suppressions.
- Do not raise `minSdk` merely to avoid compatibility branches. Any increase
  requires usage evidence, release impact analysis, and an ADR.

## Verification expectations

Run the checks relevant to the changed layer:

- build configuration: clean debug and release builds, lint, and dependency
  resolution;
- pure input logic: unit tests for compose/dead keys, modifiers, key dispatch,
  layout parsing, and suggestions;
- UI or IME lifecycle: instrumentation tests plus manual testing on the Android
  versions in `docs/testing-strategy.md`;
- native code: build every supported ABI and test dictionary lookup on a 16 KB
  page-size image;
- resource changes: compile all resource qualifiers and inspect representative
  layouts; avoid mass formatting because it hides semantic differences.

Record commands run and any untested behavior in the change description.

## Mandatory local and CI gates

- Every production change requires tests. A bug fix starts with a reproducing
  test when technically practical; otherwise the PR must explain why and add a
  documented manual regression case.
- Install the local checks with `prek install --prepare-hooks` and run the full
  suite before opening or updating a PR with `prek run --all-files`.
- The same prek configuration runs in GitHub Actions on free `ubuntu-latest`
  runners. Local and CI commands must remain equivalent.
- Android build, lint, unit, instrumentation, native, and device checks are
  added to both local automation and CI as their toolchains become available.
- Do not bypass hooks with `--no-verify`. An emergency exception requires the
  repository owner's explicit approval and must be recorded in the PR.
- If free GitHub-hosted runners become insufficient, document the workload and
  required labels before enabling owner-provided runners.

## Pull request merge contract

No PR may merge until all of the following are complete:

1. required local checks and GitHub Actions checks pass;
2. Codex has reviewed the final diff and all actionable findings are resolved;
3. the local Claude CLI has reviewed the final diff and all actionable findings
   are resolved;
4. the repository owner has approved the PR;
5. the branch is current with `v2` and the final reviewed commit SHA is the one
   being merged.

After any material code change made in response to review, rerun tests and both
model reviews. Model review supplements human ownership; it never authorizes a
merge by itself. Record review evidence in the PR checklist. Never include
secrets, signing material, user text, or other sensitive data in model prompts.

Prefer squash merge for one-issue PRs. The resulting commit should retain the
issue reference and any required upstream attribution.

## Fork policy

The configured research remotes are:

- `max-pulya` — `max-pulya/hackers_keyboard_by_max_pulya`
- `crab182` — `crab182/hackerskeyboard`
- `hongkongphoooey` — `hongkongphoooey/hackerskeyboard-fork`
- `upstream` — canonical `klausw/hackerskeyboard`; inspect its issues and pull
  requests as part of planning and before implementing compatibility fixes

Use fork code as evidence and as a source of small candidate patches. Before
porting a change:

1. identify the exact commit and its parent assumptions;
2. inspect the diff for unrelated behavior and security/privacy implications;
3. reimplement or cherry-pick only the minimal coherent change;
4. add a regression test first when practical;
5. record the decision and provenance in `docs/fork-research.md`;
6. verify license compatibility and preserve attribution.

Never merge a fork branch wholesale. In particular, do not import `diyRAG/` from
`crab182`, bulk resource formatting from `hongkongphoooey`, or hard-coded
application-specific commands from `max-pulya`.

Before starting an issue, search canonical upstream issues and PRs for the same
behavior. Link relevant upstream items in the local issue and classify them as
evidence, candidate patch, duplicate report, or rejected approach. Treat
attachments and AI-generated archives in upstream issues as untrusted input;
never execute or import them without isolated inspection.

## Documentation

- Update the relevant stage checklist when work lands.
- Add an ADR under `docs/adr/` for irreversible choices such as a minimum-SDK
  increase, replacing JNI, changing the layout format, or adopting Compose for
  the keyboard UI.
- Keep documentation factual: distinguish verified builds/tests from claims in
  commit messages or README files.
