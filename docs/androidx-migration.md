# План миграции на AndroidX

Статус: S2.01 завершён в #48; S2.02 реализуется в
[#50](https://github.com/adeepn/hackerskeyboard/issues/50), 13 августа 2026 года.
S2.03 ещё не реализован.

## Решение

Миграция делится на два независимых PR:

1. S2.02 заменяет только Support Library и Android Support Test на AndroidX,
   удаляет неиспользуемые зависимости и сохраняет `targetSdk 26`.
2. S2.03 отдельно переводит settings UI с platform `android.preference` на
   `PreferenceFragmentCompat` и добавляет тесты совместимости настроек.

Повышение `targetSdk` начинается только после этих изменений, последовательно в
S2.13–S2.18. Manifest, PendingIntent, receiver, notification permission и другие
runtime-изменения не входят в AndroidX PR.

## Проверенная текущая поверхность

### Production

| Текущая зависимость/API | Фактическое использование | Решение |
|---|---|---|
| `com.android.support:support-compat:26.0.0` | `NotificationCompat` и `NotificationManagerCompat` в `LatinIME` | S2.02: заменить на AndroidX Core и только два соответствующих import |
| `com.android.support:appcompat-v7:27.1.1` | AppCompat classes, themes и ActionBar в проекте не найдены | S2.02: удалить, не заменять на `androidx.appcompat` |
| `android.preference` platform API | 11 Java-файлов и 6 XML preference resources | S2.03: отдельная миграция settings UI |
| `app/libs/voiceimeutils.jar` | Только platform Android/Java classes; ссылок на Support Library или AndroidX не обнаружено | Не является основанием включать Jetifier; замена JAR остаётся S2.09 |

В `android.preference` inventory входят пять наследников `PreferenceActivity`:
главный settings activity, language selection и три вложенных экрана. Ещё три
класса реализуют custom preferences, а три IME-класса читают default
preferences. Их перенос затрагивает lifecycle, navigation, themes и XML class
names, поэтому механическая смена imports небезопасна.

### Instrumentation tests

| Текущий элемент | Использование | Решение S2.02 |
|---|---|---|
| `android.support.test.runner.AndroidJUnitRunner` | runner в `defaultConfig` | заменить AndroidX Test runner |
| `android.support.test.InstrumentationRegistry` | два smoke-test файла | заменить на AndroidX Test registry API и сохранить target-context semantics |
| `android.support.test.runner.AndroidJUnit4` | два smoke-test файла | заменить AndroidX JUnit4 runner |
| Support Espresso 3.0.2 | dependency объявлена, прямых Espresso imports нет | удалить либо обосновать первым реальным Espresso test; не переносить пустую зависимость автоматически |

## Контракт S2.02: Core и Test

S2.02 должен быть dependency-only migration с минимальными import/test API
изменениями:

- включить `android.useAndroidX=true`;
- выбрать и зафиксировать совместимые стабильные версии AndroidX Core и AndroidX
  Test по официальным release notes на дату реализации;
- удалить Support Library artifacts и не добавлять AppCompat;
- не включать `android.enableJetifier`, пока dependency report или bytecode
  inspection не покажут Support Library references в стороннем артефакте;
- сохранить `applicationId`/namespace `com.baodeep.hackerskeyboard`,
  `minSdk 24`, `targetSdk 26`, 66 preference keys и внешний dictionary action
  `org.pocketworkstation.DICT`;
- не менять notification behavior, intents, manifest, layouts или defaults.

До и после изменения нужно сохранить dependency reports для debug и
instrumentation configurations. В итоговом graph не должно остаться artifacts
`com.android.support:*`; одновременно не должно появиться неиспользуемого
`androidx.appcompat` или транзитивного Kotlin workaround.

### Проверки S2.02

- `prek run --all-files`;
- clean dependency resolution;
- debug и release APK build;
- Android lint без глобальных suppressions;
- unit tests;
- merged-manifest/application identity checks;
- application и JNI dictionary smoke на API 24 и API 37;
- проверка APK ABI и package/label;
- negative source/dependency guard против возврата `android.support`.

Если AndroidX API требует runtime-изменения, не связанные с imports, S2.02
останавливается и создаётся отдельная compatibility issue вместо расширения PR.

### Выбранные версии S2.02

По официальным AndroidX release notes на 13 августа 2026 года выбраны стабильные
версии:

- `androidx.core:core:1.19.0`;
- `androidx.test:runner:1.7.0`;
- `androidx.test.ext:junit:1.3.0`.

AndroidX Core 1.19.0 требует `compileSdk 37` и AGP 9.1.0 или новее. Проект не
откатывает библиотеку: S2.02 атомарно поднимает `compileSdk` до 37, AGP до 9.1.1
и обязательный для него Gradle wrapper до 9.3.1. `targetSdk 26` остаётся без
изменений, поэтому platform behavior migration по-прежнему выполняется
отдельными checkpoints.

Источники: [Core release notes](https://developer.android.com/jetpack/androidx/releases/core)
и [AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test).
Неиспользуемые AppCompat и Espresso удаляются без замены. Jetifier остаётся
выключенным; `scripts/verify-androidx-migration.py` фиксирует dependencies,
runner/imports и отсутствие Support Library references в bundled JAR.
`scripts/verify-resolved-androidx.sh` дополнительно проверяет реальные Gradle
graphs для debug runtime и instrumentation test runtime в CI на JDK 17.

## Контракт S2.03: settings

Цель S2.03 — заменить `PreferenceActivity`/platform preference widgets на
`PreferenceFragmentCompat`, сохранив данные и поведение. Application-ID migration
уже означает, что v1 private storage не читается напрямую; этот PR не должен
создавать скрытый cross-package importer.

Перед миграцией фиксируются тестами:

- точный набор из 66 preference keys;
- default value и тип каждого значения;
- чтение уже сохранённых boolean, string и string-set значений;
- главный экран, вложенные actions/view/feedback screens и language selection;
- summary для list/edit preferences;
- динамическое создание language checkboxes;
- изменение настройки во время жизни IME и listener registration/unregistration;
- запуск settings activities из launcher, IME и notification flows;
- process recreation и смена ориентации.

XML можно переводить по одному экрану, но нельзя одновременно переименовывать
ключи, менять defaults, реструктурировать весь settings UX или внедрять Compose.

## Оценка upstream и форков

| Источник | Полезное | Не переносить |
|---|---|---|
| [upstream PR #867](https://github.com/klausw/hackerskeyboard/pull/867), head `13f4c54` | подтверждает два notification imports и AndroidX Test runner | устаревшие versions, `minSdk`/`targetSdk` change, необоснованный Jetifier, wrapper и cleanup в одном PR |
| [upstream PR #989](https://github.com/klausw/hackerskeyboard/pull/989), head `54d39f6` | удаляет AppCompat и оставляет Core/Test | target 35, PendingIntent behavior, toolchain, app defaults и version change смешаны с миграцией |
| `hongkongphoooey/master` @ `9f1d768` | подтверждает Core/Test/AppCompat mapping как checklist | AppCompat здесь также не нужен; не брать Jetifier, global Kotlin resolution, `minSdk 29`, mass formatting и platform fixes |
| `max-pulya/master` @ `981313f` | AndroidX migration отсутствует | старые Support Library dependencies |
| `crab182/master` @ `e4d7422` | AndroidX migration отсутствует | unrelated `diyRAG/` и WIP branches |

Код из этих источников в S2.01 не импортировался. S2.02 реализуется независимо
маленьким diff; ссылки используются как provenance исследования и negative
scope examples.

## Порядок следующих задач

1. Закрыть S2.01 документационным PR.
2. Завести и выполнить S2.02 с dependency/import migration и полной CI matrix.
3. Завести S2.03 с characterization/migration tests для settings.
4. После зелёных S2.02/S2.03 перейти к platform/API issues S2.04–S2.12.
5. Повышать `targetSdk` только отдельными checkpoints S2.13–S2.18.
