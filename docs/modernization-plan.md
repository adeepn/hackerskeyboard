# План модернизации v2

## Цель и ограничения

Цель — выпустить поддерживаемую версию Hacker's Keyboard с `targetSdk 36`, не
потеряв поведение, ради которого используется проект. Миграция идёт четырьмя
последовательными этапами. Feature work допускается отдельно от migration work,
чтобы регрессии можно было локализовать.

Организация работ выполняется через четыре GitHub milestones и четыре tracking
issues. Каждый нижележащий шаг является отдельной issue и, если меняет
репозиторий, отдельным PR в `v2`. Полная декомпозиция приведена в
`work-breakdown.md`, обязательные gates — в `development-process.md`.

Не входят в обязательный объём первого релиза v2:

- полный rewrite на Kotlin;
- Compose keyboard surface;
- замена формата XML-раскладок;
- переписывание словаря с C++ на Kotlin/Rust;
- облачные функции, аккаунты или телеметрия.

## Этап 1. Воспроизводимая современная сборка

### Результат

Исходное поведение ещё не меняется, но debug и release APK собираются
воспроизводимо современным toolchain.

### Работы

1. Добавить полный Gradle wrapper и executable bit для `gradlew`.
2. Зафиксировать JDK 17 в документации и CI.
3. Перейти на AGP 8.13.x и Gradle 8.13 как консервативный мост к API 36. После
   стабильного релиза отдельно оценить AGP 9.x.
4. Перейти с `jcenter()` на `mavenCentral()` и `google()`.
5. Добавить `namespace`, современный Android DSL и `compileSdk 36`.
6. Сначала оставить `targetSdk 26`, чтобы отделить build migration от platform
   behavior migration.
7. Зафиксировать NDK/CMake и поддерживаемые ABI.
   Минимальная версия v2 — Android 7.0 / API 24 согласно ADR-0001.
8. Убрать `debuggable true` из release, включить release lint и baseline только
   для осознанно принятых legacy warnings.
9. Создать CI: assemble debug/release, unit tests, lint, dependency report.
10. Зафиксировать baseline APK: размер, permissions, resources, native libs и
    smoke behavior на контрольном устройстве.
11. Подключить `prek` как единый локальный pre-commit runner и запускать ту же
    конфигурацию на `ubuntu-latest` в GitHub Actions.

### Критерии завершения

- clean checkout собирается одной командой wrapper на JDK 17;
- debug и non-debuggable release APK успешно создаются;
- все ABI содержат `libjni_pckeyboard.so` и проходят smoke lookup;
- нет `jcenter()` и недекларированных локальных prerequisites;
- `compileSdk 36`, при этом функциональный diff отсутствует либо документирован;
- CI повторяет локальные проверки.

### Риски

- старые Support Library artifacts могут конфликтовать с новым toolchain;
- старый C++ может не компилироваться новым NDK;
- AGP может обнаружить ошибочные ресурсы, ранее пропускавшиеся.

Эти ошибки исправляются минимальными патчами; AndroidX migration относится к
этапу 2.

## Этап 2. AndroidX и targetSdk 36

### Результат

Приложение устанавливается и работает с современными security/platform rules и
может быть представлено в Google Play.

### Работы

1. Support Library → AndroidX Core/AppCompat/Preference/Test.
2. `android.preference` → `PreferenceFragmentCompat`, сохранив имена preference
   keys и данные существующих пользователей.
3. Исправить manifest:
   - fully qualified/relative component names;
   - минимально необходимые `android:exported`;
   - `<queries>` только для двух поддерживаемых dictionary contracts и speech
     recognition, если он остаётся;
   - актуальные backup/data extraction rules;
   - `POST_NOTIFICATIONS` только для опционального keyboard notification.
4. Pending intents сделать explicit и immutable, если mutability не требуется.
5. Runtime receivers регистрировать через AndroidX с корректными
   `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED`; package-change receiver оставить
   системным и проверить data scheme `package`.
6. Заменить внутренние implicit broadcasts notification actions явными intents
   или activity/service entry points.
7. Реализовать notification permission UX; отказ не должен влиять на IME.
8. Заменить старый voice JAR публичным `RecognizerIntent`/поддерживаемым
   контрактом либо временно скрывать voice action при отсутствии сервиса.
9. Package visibility: не использовать `QUERY_ALL_PACKAGES`.
10. Обновить vibration, locale/resources и PackageManager overloads.
11. Удалить или заменить `AsyncTask` там, где lifecycle/cancellation уже создают
    ошибки; полная декомпозиция остаётся этапом 4.
12. Поднимать target последовательно через поведенческие границы
    28 → 31 → 33 → 34 → 35 → 36, фиксируя результаты тестов.
13. Проверить edge-to-edge/window insets, popup placement, candidates view,
    extract/fullscreen mode, portrait/landscape и multi-window.
14. Собрать Android App Bundle и выполнить Play pre-launch проверки.

### Критерии завершения

- `compileSdk 36`, `targetSdk 36`, выбранный `minSdk` обоснован данными;
- AndroidX, без Support Library и Jetifier-зависимости в финальном состоянии;
- нет `QUERY_ALL_PACKAGES`, отсутствующих exported/receiver/pending-intent flags;
- отказ в notification permission не ломает клавиатуру;
- dictionary packs обнаруживаются через узкие queries;
- нет runtime crash на Android 12, 13, 14, 15 и 16 в основной матрице;
- native library протестирована на 16 KB page-size image;
- release AAB проходит lint и базовую Play проверку.

## Этап 3. Защита функциональности тестами

### Результат

Существующее поведение описано автоматическими и ручными regression tests. После
этого архитектурные изменения становятся контролируемыми.

### Автоматические тесты

1. Characterization tests для compose/dead accents и Unicode.
2. Modifier state matrix: Shift/Caps/Ctrl/Alt/Meta/Fn, sticky/chording и смена
   языка в каждом состоянии.
3. XML layout parser для всех resource variants, уникальность/валидность codes,
   popup references и размеры рядов.
4. `EditorInfo` matrix: text, password, number, phone, URI, email, multiline,
   auto-complete, no-suggestions.
5. `InputConnection` fake tests: commit/composing/delete-surrounding, arrows,
   Tab/Esc, Ctrl combinations и ConnectBot/terminal fallbacks.
6. Suggestion/dictionary tests: lookup, bigrams, locale switching, corrupted or
   missing dictionary, plugin discovery.
7. Instrumentation tests IME lifecycle: create/start/finish/restart input,
   rotate, configuration change, process recreation.
8. Screenshot/golden tests основных 4/5-row layouts, themes, candidates и popup.
9. Native tests по ABI и page size; malformed dictionary corpus.

### Ручные сценарии

- включение/выбор IME и первый запуск;
- обычный набор, autocap, autocorrect, выбор suggestion;
- long press и popup на крайних клавишах;
- одновременный modifier + key;
- смена языка жестом/клавишей и возврат индикаторов;
- аппаратная клавиатура;
- Termux/ConnectBot/SSH, браузер, мессенджер, password manager, number/PIN field;
- split screen, планшет, portrait/landscape, gesture navigation;
- уведомление включено, выключено и permission denied;
- dictionary pack installed/removed/replaced во время жизни IME.

### Критерии завершения

- вся критическая матрица имеет автоматический тест или документированный manual
  case;
- известные отличия от v1 перечислены и одобрены;
- zero-crash smoke run на каждом поддерживаемом Android API tier;
- найденные fork fixes сначала воспроизводятся тестом, затем портируются;
- release checklist можно выполнить другим разработчиком по документации.

## Этап 4. Постепенная модернизация архитектуры

### Результат

Код проще изменять, при этом каждый рефакторинг защищён тестами этапа 3.

### Работы

1. Выделить из `LatinIME` session/lifecycle, dispatch, modifiers, suggestions,
   feedback, notification и settings controllers.
2. Ввести интерфейс над `InputConnection`, чтобы ввод тестировался без живого
   IME service.
3. Отделить immutable layout model от Android resource parsing и rendering.
4. Перенести background work на executors или Kotlin coroutines с явным scope и
   cancellation.
5. Создать typed settings repository с миграцией старых preference keys.
6. Новый изолированный код писать на Kotlin; старые классы переводить только
   при наличии тестов и реальной выгоды.
7. Оценить новый settings UI отдельно. Compose допускается только после ADR и
   проверки размера/старта; keyboard surface остаётся custom View.
8. Измерить JNI dictionary latency, память и crash surface. Решение оставить,
   переписать на безопасный C++/Rust либо портировать принимается отдельным ADR.
9. Ввести performance benchmarks: startup, first input view, key-to-commit
   latency, layout switching, dictionary load и allocations при наборе.
10. Добавлять функции из форков только как независимые opt-in features без
    hard-coded package policies.

### Критерии завершения

- `LatinIME` является coordinator, а не владельцем всей реализации;
- ключевая логика тестируется без emulator;
- нет `AsyncTask`, глобального изменения resource locale и непрозрачного voice
  JAR;
- новые компоненты имеют ясное ownership/lifecycle;
- performance не хуже утверждённого baseline;
- решение о Kotlin/Compose/JNI основано на данных и зафиксировано ADR.

## Порядок релизов

- `v2-dev`: завершён этап 1, внутренние APK.
- `v2-alpha`: target 36 и AndroidX, основная функциональность работает.
- `v2-beta`: завершена критическая матрица этапа 3, тестирование пользователями.
- `v2.0`: Play-ready release без известных блокирующих регрессий.
- `v2.x`: архитектурный этап 4 и отобранные новые функции.

## Общий Definition of Done

Каждое изменение должно иметь ограниченный diff, проверку соответствующего
слоя, описание пользовательского эффекта, результат тестирования и обновлённую
документацию. Коммит из форка дополнительно должен иметь ссылку на source commit
и решение: ported, adapted, deferred или rejected.
