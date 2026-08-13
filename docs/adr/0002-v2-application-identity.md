# ADR-0002: Permanent v2 application identity

- Status: accepted
- Date: 2026-08-13
- Decision owner: repository owner

## Context

The historical Hacker's Keyboard is installed and published as
`org.pocketworkstation.pckeyboard`. Android treats the application ID as the
permanent device and store identity, and permits an update only when both the
application ID and signing certificate match. This repository does not control
the historical release key, so an independently signed v2 APK must not claim to
be an update of that package.

The repository owner controls the `baodeep.com` domain and selected
`com.baodeep.hackerskeyboard` in #26. An owned-domain reverse-DNS identifier is
independent of the current source hosting account. The code namespace, XML
custom-view names and JNI registration were still coupled to the historical
package.

External dictionary packs use the independent legacy discovery action
`org.pocketworkstation.DICT`. That action is an interoperability protocol, not
the application identity. Existing preference keys also form a compatibility
contract even though Android keeps their storage private to each application.

## Considered options

1. Keep `org.pocketworkstation.pckeyboard`. Rejected because an independently
   signed APK collides with the historical installation and cannot update it.
2. Change only `applicationId` and retain the old code namespace. Rejected
   because it leaves manifest, resources, tests and JNI split across two names
   before broader v2 migration.
3. Use `io.github.baodeep.hackerskeyboard`. Rejected after confirming control
   of `baodeep.com`: it is valid, but unnecessarily couples the permanent app
   identity to the current source-hosting namespace.
4. Rename both identities to `com.baodeep.hackerskeyboard`. Accepted as a
   permanent, owner-controlled identity with explicit side-by-side semantics
   and no dependency on a GitHub account or organization name.

## Decision

- permanent v2 `applicationId` is `com.baodeep.hackerskeyboard`;
- Android namespace and Java package use the same value;
- v2 private intent actions use the new application identity;
- external action `org.pocketworkstation.DICT` remains unchanged so existing
  Hacker's Keyboard dictionary packs stay discoverable;
- the AnySoftKeyboard dictionary contract remains unchanged;
- existing preference keys remain byte-for-byte unchanged;
- v2 is a separate application and may coexist with v1;
- v1 settings and Android's enabled/selected IME state are not migrated
  automatically. A future explicit export/import feature must be designed and
  tested separately.

## Signing and publication consequences

Changing identity prevents collision with v1 but does not itself establish an
update chain. Every published v2 release must use one stable protected release
key from the first public build onward. CI release APKs remain unsigned until
the owner-managed signing path in #45 exists. GitHub-hosted debug builds use an
ephemeral debug certificate and therefore prove fresh installation, not the
ability to update a debug APK downloaded from a different workflow run.

No private key, keystore or signing secret is committed to the repository.

## Consequences and rollback

- v1 and v2 have separate private data, backup namespaces, permissions and IME
  enablement;
- users must enable v2 in Android settings even if v1 was already enabled;
- uninstalling either application does not uninstall the other;
- after public release under the accepted ID, changing it again creates a third
  application rather than an upgrade;
- before publication the source change can be reverted, but Android cannot move
  private app data between IDs merely by reverting source;
- internal package-name changes touch many files mechanically, so CI guards the
  identity, old-identifier residue, preference keys, component loading and JNI.

## Evidence

- owner decision and confirmation of `baodeep.com` control: #26;
- bounded implementation: #44;
- stable signing/update-chain follow-up: #45;
- canonical upstream has no issue or PR implementing an independent package
  identity migration;
- all three researched forks retain `org.pocketworkstation.pckeyboard` and
  provide no migration patch;
- API 24 and API 37 instrumentation tests provide the installation, component
  and JNI baseline that the renamed package must preserve.
