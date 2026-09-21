# Target 36: приоритетный Play bootstrap

Решение владельца от 21 сентября 2026; задача [#95](https://github.com/adeepn/hackerskeyboard/issues/95).
Вместо отдельных target-релизов 28/31/33/34/35 выполняется прямой переход
26 → 36. Цель — первый Internal testing выпуск и затем автоматическая доставка,
не объявление всей модернизации законченной.

Кандидат: `2.0.0-alpha05`, `versionCode 2000005`; package/сертификат сохраняются.
`compileSdk 37`, `minSdk 24`, AndroidX, NDK 29 и toolchain не меняются.

## Минимальные изменения

- `targetSdk 36` и guards готовых APK/AAB; версия выше загруженной alpha04.
- Settings Activity из IME запускается с `FLAG_ACTIVITY_NEW_TASK`: старый вызов
  из Service без этого флага не является допустимым контрактом современного target.
- Шесть самостоятельных Activity получают insets padding для системных панелей,
  вырезов и экранной клавиатуры на API 35+. Insets не накапливаются и не
  применяются повторно дочерними views. IME surface/candidates геометрия этим
  helper не меняется; известный отступ на OnePlus остаётся отдельной проверкой.
- Сохранены настройки, voice-IME route #94 и старый recognizer fallback. Удаление
  JAR — #93, не условие запуска тестового канала само по себе. Старый fallback
  не имеет `ServiceHelper` в manifest и не считается проверенным рабочим путём.

## Проверки

- Local/CI: `prek run --all-files`, JVM/lint/build gates, debug/release APK и AAB.
- `scripts/test-native-alignment.py` проверяет положительные/отрицательные
  ELF/ZIP fixtures. `verify_native_alignment.py` проверяет все 64-bit `.so`:
  PT_LOAD alignment >=16 KB, согласованность offset/address, bounds и ZIP
  alignment несжатых библиотек в APK. AAB ZIP layout сам по себе не является
  APK layout; в AAB проверяются ELF. Обе 64-bit ABI обязательны.
  Дополнительно `bundletool dump config` должен подтвердить
  `PAGE_ALIGNMENT_16K` для APK, которые Play создаст из bundle.
- `BinaryDictionarySmokeTest` проверяет настоящий `targetSdk 36`; в managed
  device jobs сверяет `Os.sysconf(_SC_PAGESIZE)` с ожидаемым значением перед JNI
  lookup. API 36 использует FORCE_16KB_PAGES, API 37 — 4 KB, API 24 сохраняется.
- `Target36CompatibilityTest` проверяет повторные insets на всех шести Activity
  и настоящую IME session на `Main` editor: enable/select, dispatch `a`, `b`,
  delete, `c` через `LatinIME.onKey` → InputConnection, результат `ac`, settings
  launch из сервиса. Это не touch/gesture test. Default/enabled IME возвращаются
  в `finally`; использовать только disposable emulator/test device installs.

Локальные device-команды при наличии JDK 21 и SDK (те же, что CI):

```sh
scripts/run-api24-connected-tests.sh
./gradlew :app:pixel2Api36_16kDebugAndroidTest --no-daemon --stacktrace \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  -Pandroid.testInstrumentationRunnerArguments.expectedPageSize=16384
./gradlew :app:pixel2Api37DebugAndroidTest --no-daemon --stacktrace \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  -Pandroid.testInstrumentationRunnerArguments.expectedPageSize=4096
```

Результаты конкретных прогонов фиксируются в PR. Не подменять проверку 16 KB
тем фактом, что современный NDK использован или APK собрался.

Lint: убран устаревший baseline finding `ExpiredTargetSdkVersion` для target 26.
У строки target 36 стоит точечный `OldTargetApi` suppression: AGP рекомендует
уже 37, но согласованный bootstrap намеренно использует 36. Остальные lint
категории остаются включёнными, новых findings в baseline не добавляется.

## Что остаётся до/после загрузки

Перед загрузкой owner-signed кандидата: на OnePlus 13 проверить update без
uninstall, включение/выбор IME, обычный набор, Shift/Ctrl/Alt/Fn, настройки,
портрет/ландшафт, candidates/отступ, popup, Back и notification deny/grant.
На device с внешними словарями/voice providers проверить их обнаружение.
Системный permission dialog и revoke acceptance остаются в #89; package
visibility fixtures/геометрия/полная матрица Android — незакрытые regression
риски, не «пройденные» сценарии. При конкретном сбое исправлять его до принятия
кандидата; не требовать предварительного общего рефакторинга.

После merge и зелёного `v2`: подписать APK/AAB отдельным существующим workflow,
повторно загрузить AAB в Console (#81), сохранить фактические ошибки/acceptance.
Только после первого принятого выпуска подключить автоматическую internal
публикацию (#82). Production rollout не разрешён этим решением.

## Исследование

Upstream [#958](https://github.com/klausw/hackerskeyboard/issues/958) — evidence
проблемы Store availability; [#957](https://github.com/klausw/hackerskeyboard/issues/957)
— landscape clipping, [#964](https://github.com/klausw/hackerskeyboard/issues/964)
— candidates/blank bar. Последние два не доказывают, что смена target исправляет
геометрию. PR search `targetSdk` не дал результатов. Чужой код не импортирован.

Официальные основания (проверены 21 сентября 2026):
[Play target API](https://developer.android.com/google/play/requirements/target-sdk),
[Android 16 target behavior](https://developer.android.com/about/versions/16/behavior-changes-16),
[16 KB support](https://developer.android.com/guide/practices/page-sizes).
