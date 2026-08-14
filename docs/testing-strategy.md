# Стратегия тестирования v2

## Почему обычного UI smoke test недостаточно

IME работает внутри окон других приложений и зависит от их реализации
`InputConnection`. Ошибка может проявиться только с конкретным `inputType`, при
смене ориентации, в popup, при удержании двух клавиш или после пересоздания
service. Поэтому тесты разделены на быстрые детерминированные слои и device
matrix.

## Пирамида проверок

| Уровень | Что проверяется | Когда запускать |
|---|---|---|
| JVM unit | state machines, compose/dead keys, parsing, suggestions | каждый PR |
| Native host/device | dictionary format, malformed input, ABI | изменение C++/NDK и nightly |
| Instrumentation | IME lifecycle, resources, settings migrations | каждый migration PR |
| Screenshot | layouts, themes, popup/candidates geometry | resource/UI PR и nightly |
| Manual interoperability | реальные editors и terminal apps | alpha/beta/release |

## Реализованный Stage 1 application и native smoke

Instrumented tests запускаются на Android Emulator / API 24 и Gradle Managed
Device Pixel 2 / API 37. `ApplicationSmokeTest` подтверждает, что установленный
APK зарегистрирован системой как IME, а launcher/setup и legacy settings
activities запускаются и создают обязательные views. `BinaryDictionarySmokeTest`
проверяет lookup встроенного минимального словаря через полную границу Java →
JNI → C++. Локальные команды описаны в `android-build-toolchain.md`.

Эти smoke tests подтверждают установку test target, регистрацию IME, загрузку
основных UI resources, загрузку библиотеки, совместимость JNI signatures и один
positive/negative lookup. Они не вводят текст через экранную клавиатуру и не
заменяют Stage 3 lifecycle/input/layout matrix, тесты полноценного corpus,
невалидных словарей и bigrams, а также отдельную проверку 16 KB page size.

S1.25 добавляет быстрый identity guard в `prek`. Он фиксирует постоянные
`applicationId`/namespace, отсутствие исторического и отвергнутого временного
app identifier в Android inputs, новый JNI class path, сохранение
`org.pocketworkstation.DICT` и неизменность 66 legacy preference keys. Device
smoke дополнительно утверждает новый package name на API 24 и API 37. APK
manifest identity проверяется после сборки в CI.
Когда Stage 2 добавит release AAB job, тот же gate должен проверять identity в
bundle до публикации. Будущий settings export/import обязан иметь round-trip
tests для всех зафиксированных ключей, неизвестных/повреждённых значений и явно
доказывать, что он не пытается читать private storage пакета v1 напрямую.

## API и устройства

Минимальная обязательная матрица должна включать:

- нижнюю границу `minSdk`;
- Android 8/API 26 как legacy контроль, пока он поддерживается;
- Android 10/API 29;
- Android 12/API 31 — exported и pending intents;
- Android 13/API 33 — notifications и receiver APIs;
- Android 14/API 34 — runtime receiver restrictions;
- Android 15/API 35 — 16 KB support и window behavior;
- Android 16/API 36 — обязательная compatibility boundary;
- Android 17/API 37 — целевой релиз;
- минимум одно 16 KB page-size устройство/образ;
- phone и tablet profiles, portrait и landscape.

Если `minSdk` будет поднят, решение и данные о доле исключённых пользователей
фиксируются ADR.

## Критическая функциональная матрица

### Набор и редактор

- commit одного символа и последовательности;
- composing text и завершение слова;
- delete/backspace и delete surrounding text;
- cursor arrows, Home/End, Tab/Esc;
- Unicode, combining characters, RTL и surrogate pairs;
- отсутствие подсказок/истории в password fields.

### Состояния клавиатуры

- Shift tap, double tap, caps lock и chording;
- Ctrl/Alt/Meta/Fn отдельно и одновременно;
- sticky modifier preferences;
- смена языка и layout mode при активном модификаторе;
- повтор клавиши, multitouch и gesture cancellation.

### Геометрия

- 4-row, 5-row, compact, full, extension и phone layouts;
- popup у левого/правого/верхнего края;
- candidates visible/hidden;
- все темы, высоты и hint modes;
- orientation, split screen, display cutout и gesture insets.

### Жизненный цикл и интеграции

- enable/select IME;
- start/restart/finish input и process death;
- dictionary pack install/remove/replace;
- optional notification и denied permission;
- settings upgrade со старой версии;
- system locale и выбранный input locale меняются независимо.

## Контроль производительности

Для baseline и каждого milestone измеряются:

- cold start service;
- время до первого input view;
- key-down → `InputConnection` commit;
- переключение языка/режима;
- загрузка основного и plugin dictionary;
- allocations и пропущенные frames при непрерывном наборе.

Порог допустимой регрессии определяется после первого воспроизводимого baseline.
До его определения любое заметное ухудшение требует расследования.

## Release evidence

Перед релизом сохраняются версии toolchain, hash коммита, результаты CI,
устройства/API, результат 16 KB test, список известных отклонений и ручной
checklist. Утверждение «работает на Android N» без устройства/образа и сценария
не считается проверкой.

Для каждого подписанного alpha APK workflow дополнительно проверяет package,
`versionCode`, `versionName`, SHA-256 сертификата и подпись `apksigner`, а также
сохраняет SHA-256 самого APK в `release-evidence.txt`. Release build проходит
R8/resource shrinking, JNI smoke на debug variant защищает Java/native
контракт, а keep guards фиксируют JNI class name и динамически найденный
`@raw/main`. Размер unsigned release ограничен CI budget 3 400 000 байт.

Первый APK со стабильным owner key проверяется свежей установкой. Если на
устройстве уже стоит CI/debug APK `com.baodeep.hackerskeyboard` с эфемерным
сертификатом, его нужно один раз удалить. Затем проверяются последовательные
обновления только APK, подписанными тем же owner key и с возрастающим
`versionCode`.

S1.29 фиксирует первый обязательный update-chain case: owner-signed alpha02 с
`versionCode 2000002` устанавливается поверх owner-signed alpha01 с
`versionCode 2000001` на OnePlus 13 / Android 16 без uninstall. После update
владелец проверяет сохранение настроек, enable/select state IME и базовый ввод.
