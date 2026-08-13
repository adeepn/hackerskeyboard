# Карта задач v2

Это исходная декомпозиция для GitHub milestones и issues. Номера issues будут
добавлены после создания на GitHub.

## Milestone: V2 / Stage 1 — Reproducible Build

Epic: **Stage 1: восстановить современную воспроизводимую сборку**.

- S1.01 — добавить полный Gradle wrapper;
- S1.02 — зафиксировать JDK 17 и developer setup;
- S1.03 — обновить Gradle и AGP до выбранных совместимых версий;
- S1.04 — заменить `jcenter()` на `mavenCentral()`;
- S1.05 — добавить `namespace` и современный Android DSL;
- S1.06 — поднять `compileSdk` до 36 без изменения `targetSdk`;
- S1.07 — исправить build/resource errors нового AGP;
- S1.08 — зафиксировать NDK, CMake и ABI;
- S1.09 ([#31](https://github.com/adeepn/hackerskeyboard/issues/31)) — выполнить
  Java → JNI → C++ smoke dictionary lookup на API 36; сборка и упаковка JNI
  восстановлены в S1.07 / PR #29;
- S1.10 — исправить debug/release configuration;
- S1.11 — включить lint и создать осознанный legacy baseline;
- S1.12 — добавить `prek` и локальные базовые проверки;
- S1.13 — добавить GitHub Actions на `ubuntu-latest`;
- S1.14 — добавить Gradle build/lint/unit jobs в CI;
- S1.15 ([#42](https://github.com/adeepn/hackerskeyboard/issues/42)) — снять
  baseline APK и baseline behavior на API 24/36;
- S1.16 — описать reproducible developer setup и troubleshooting.
- S1.17 — исправить legacy executable file modes отдельным механическим PR;
- S1.18 — спланировать поэтапный whitespace/line-ending cleanup без массового
  функционального diff.
- S1.19 — декомпозировать upstream PR #978 (SDK 36) на проверяемые candidate
  patches и negative regression cases;
- S1.20 — сравнить компактный upstream PR #989 с #978 и нашим toolchain plan;
- S1.21 — извлечь только пригодные тестовые идеи из закрытого upstream PR #980.
- S1.22 — выполнить licensing/provenance inventory исходников, ресурсов, JNI,
  словаря и `voiceimeutils.jar`;
- S1.23 — определить и добавить корректный `NOTICE`/third-party notices по
  результатам inventory;
- S1.24 — добавить CI-проверку license headers, запрещённых лицензий и состава
  license/notice в release artifacts.
- S1.25 ([decision #26](https://github.com/adeepn/hackerskeyboard/issues/26),
  [implementation #44](https://github.com/adeepn/hackerskeyboard/issues/44)) —
  сменить application identity и code namespace на
  `com.baodeep.hackerskeyboard` по ADR-0002 после зелёного baseline;
- S1.26 ([#27](https://github.com/adeepn/hackerskeyboard/issues/27)) — найти и
  лицензировать воспроизводимые источники first-party dictionary packs, сохранив
  совместимость со старым dictionary API;
- S1.27 ([#33](https://github.com/adeepn/hackerskeyboard/issues/33)) — явно
  определить ABI Gradle Managed Device до оценки перехода на AGP 9.
- S1.28 ([#45](https://github.com/adeepn/hackerskeyboard/issues/45)) — настроить
  owner-controlled release signing и стабильную v2 update chain без ключей в
  Git или доступа из обычных PR workflows.

Зависимости: S1.01 → S1.03 → S1.05/S1.06/S1.07; S1.08 → S1.09; стабильная
сборка → S1.14/S1.15. S1.12 и базовая часть S1.13 могут выполняться раньше.

## Milestone: V2 / Stage 2 — Android 16 Compatibility

Epic: **Stage 2: AndroidX и targetSdk 36**.

- S2.01 ([#48](https://github.com/adeepn/hackerskeyboard/issues/48)) —
  инвентаризация Support Library и AndroidX migration plan;
- S2.02 ([#50](https://github.com/adeepn/hackerskeyboard/issues/50)) —
  Core/AppCompat/Test → AndroidX;
- S2.03 — settings → `PreferenceFragmentCompat` с migration tests;
- S2.04 — manifest components и минимальные `android:exported`;
- S2.05 — explicit intents и PendingIntent mutability;
- S2.06 — runtime receiver export policy;
- S2.07 — узкие `<queries>` для dictionary packs;
- S2.08 — notification permission и notification UX;
- S2.09 — заменить legacy voice JAR;
- S2.10 — PackageManager API compatibility;
- S2.11 — vibration API compatibility;
- S2.12 — locale/resources migration;
- S2.13 — target SDK 28 и compatibility report;
- S2.14 — target SDK 31 и compatibility report;
- S2.15 — target SDK 33 и compatibility report;
- S2.16 — target SDK 34 и compatibility report;
- S2.17 — target SDK 35 и compatibility report;
- S2.18 — target SDK 36 и compatibility report;
- S2.19 — popup/candidates/window insets fixes;
- S2.20 — 16 KB native compatibility;
- S2.21 — release AAB и Play readiness checklist.
- S2.22 — regression tests для navigation bar/insets и самопроизвольного показа
  IME, обнаруженных в обсуждении upstream PR #978.

Target SDK steps выполняются последовательно. Независимые API migrations можно
распараллеливать при непересекающихся файлах.

## Milestone: V2 / Stage 3 — Regression Safety

Epic: **Stage 3: зафиксировать функциональность тестами**.

- S3.01 — test architecture и fixtures;
- S3.02 — compose/dead-key/Unicode tests;
- S3.03 — modifier state matrix;
- S3.04 — language switching при активных modifiers;
- S3.05 — XML layout validation всех qualifiers;
- S3.06 — `EditorInfo` input-type matrix;
- S3.07 — fake `InputConnection` test harness;
- S3.08 — terminal/navigation key tests;
- S3.09 — suggestion and dictionary tests;
- S3.10 — dictionary pack lifecycle tests;
- S3.11 — IME lifecycle instrumentation;
- S3.12 — screenshot/golden infrastructure;
- S3.13 — representative layout/theme goldens;
- S3.14 — popup/candidates geometry tests;
- S3.15 — native ABI/malformed dictionary tests;
- S3.16 — Android API/device matrix automation;
- S3.17 — manual interoperability checklist;
- S3.18 — reproduce/adapt max-pulya candidates fix;
- S3.19 — reproduce/adapt landscape width fix;
- S3.20 — reproduce/adapt Fn comma fix;
- S3.21 — reproduce/adapt modifier/language indicator fixes.
- S3.22 — upstream issue triage: notification #883/#897/#947;
- S3.23 — upstream issue triage: suggestions #901/#964;
- S3.24 — upstream issue triage: Android 15 landscape #957;
- S3.25 — upstream issue triage: popup #686 и modifier/input regressions;
- S3.26 — проверить test cases из upstream PR #980 без Kotlin rewrite.

Fork-fix issues должны зависеть от соответствующего test harness и начинаться с
красного regression test.

## Milestone: V2 / Stage 4 — Architecture and Evolution

Epic: **Stage 4: модульная архитектура и постепенный Kotlin**.

- S4.01 — определить target module boundaries и ADR;
- S4.02 — `InputConnection` abstraction;
- S4.03 — modifier state machine extraction;
- S4.04 — input dispatcher extraction;
- S4.05 — IME session/lifecycle controller;
- S4.06 — suggestion/dictionary coordinator;
- S4.07 — notification controller;
- S4.08 — sound/vibration/preview feedback controller;
- S4.09 — immutable layout model;
- S4.10 — отделить resource parsing от rendering;
- S4.11 — typed settings repository и migration;
- S4.12 — удалить `AsyncTask` и определить async lifecycle;
- S4.13 — Kotlin policy и первый изолированный Kotlin component;
- S4.14 — startup/input/dictionary benchmarks;
- S4.15 — ADR по дальнейшей судьбе JNI;
- S4.16 — ADR/исследование settings UI и Compose;
- S4.17 — generic user-defined snippets/actions design;
- S4.18 — оценка Ctrl+Backspace и mixed en/ru layouts;
- S4.19 — security/accessibility/Play review virtual gamepad proposal.

## Labels

- этапы: `stage:1`, `stage:2`, `stage:3`, `stage:4`;
- тип: `type:build`, `type:compatibility`, `type:test`, `type:refactor`,
  `type:research`, `type:feature`, `type:docs`;
- область: `area:ime`, `area:ui`, `area:dictionary`, `area:native`,
  `area:settings`, `area:ci`;
- управление: `epic`, `agent-ready`, `blocked`, `needs-device-test`,
  `breaking-risk`, `source:fork`.

Каждая issue получает ровно один stage label, один основной type label и один
или несколько area labels.
