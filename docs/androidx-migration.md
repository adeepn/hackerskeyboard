# План миграции на AndroidX

Статус: S2.01 завершён в #48; S2.02 завершён в
[#50](https://github.com/adeepn/hackerskeyboard/issues/50). S2.03 ведётся в
[#58](https://github.com/adeepn/hackerskeyboard/issues/58); первый отдельный
шаг S2.03a зафиксировал settings contract в
[#59](https://github.com/adeepn/hackerskeyboard/issues/59), S2.03b мигрирует
первый изолированный actions screen в
[#61](https://github.com/adeepn/hackerskeyboard/issues/61), S2.03c мигрирует
feedback screen и custom seek-bar dialogs в
[#63](https://github.com/adeepn/hackerskeyboard/issues/63), S2.03d мигрирует
view screen в [#65](https://github.com/adeepn/hackerskeyboard/issues/65),
S2.03e мигрирует dynamic language selection в
[#70](https://github.com/adeepn/hackerskeyboard/issues/70), S2.03f мигрирует
главный settings screen в
[#72](https://github.com/adeepn/hackerskeyboard/issues/72), S2.03g удаляет
последнюю platform preference surface в
[#74](https://github.com/adeepn/hackerskeyboard/issues/74).

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
`com.android.support:*`. S2.03b разрешает только транзитивные AppCompat artifacts,
которые входят в официальный dependency graph AndroidX Preference 1.2.1;
прямое объявление AppCompat и использование его API в source по-прежнему
запрещены static guard. Транзитивный Kotlin workaround также не допускается.

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
откатывает библиотеку: S2.02 атомарно поднимает `compileSdk` до 37, AGP до 9.3.1
и Gradle wrapper до последней стабильной версии 9.6.1. `targetSdk 26` остаётся без
изменений, поэтому platform behavior migration по-прежнему выполняется
отдельными checkpoints.

Новый lint видит существующий notification debt, относящийся к S2.05 и S2.08:
legacy content intent запускает receiver, а permission UX ещё не реализован.
До соответствующих regression tests `setNotification` имеет только узкую
method-level аннотацию для этих двух checks; глобальные suppressions и новые
baseline entries не добавляются, runtime flow не изменяется.

Источники: [Core release notes](https://developer.android.com/jetpack/androidx/releases/core)
и [AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test).
Неиспользуемые AppCompat и Espresso удаляются без замены. Jetifier остаётся
выключенным; `scripts/verify-androidx-migration.py` фиксирует dependencies,
runner/imports и отсутствие Support Library references в bundled JAR.
`scripts/verify-resolved-androidx.sh` дополнительно проверяет реальные Gradle
graphs для debug runtime и instrumentation test runtime в CI на JDK 21.

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

S2.03a зафиксировал machine-readable baseline до изменения production UI: 66
keyed XML nodes, из них 48 уникальных persisted keys (18 boolean и 30 string),
а также два programmatic string keys `selected_languages` и `input_language`.
Сохранённых float/string-set значений сейчас нет; `SeekBarPreferenceString`
намеренно хранит значения как string для совместимости. Подробности и порядок
осознанного обновления fixture описаны в `settings-contract.md`.

S2.03b добавляет официальный stable Java artifact
`androidx.preference:preference:1.2.1` и переводит только `PrefScreenActions`.
Activity создаёт initial `PreferenceFragmentCompat` синхронно только при
`savedInstanceState == null`; после configuration change восстановлением
владеет `FragmentManager`. Listener принадлежит Fragment lifecycle, live
`Preference` objects не передаются через arguments и deprecated
`setTargetFragment` не используется. Шесть string-backed actions сохраняют
прежние keys/defaults/entryValues, а summary предоставляет штатный
`ListPreference.SimpleSummaryProvider`. Экран наследует platform
`Theme.Material.NoActionBar` и добавляет только `PreferenceThemeOverlay`:
варианты Material/DeviceDefault с системным ActionBar нельзя использовать,
поскольку их decor Toolbar падает при inflate на нижней границе API 24.

S2.03c сохраняет legacy custom widgets параллельно для ещё не перенесённых
экранов и вводит AndroidX-классы с суффиксом `Compat`. Диалог является дочерним
Fragment у `PreferenceFragmentCompat`, получает в arguments только стабильный
preference key и после recreation повторно находит widget в восстановленной
иерархии. Pending slider value хранится в saved state самого диалога и не
попадает в `SharedPreferences` до подтверждения; Cancel сохраняет исходную
строку дословно. Такая граница не использует deprecated `setTargetFragment` и
не удерживает live `Preference` object. `PrefScreenFeedback` служит первым
production consumer; `vibrate_len` и `pref_click_volume` остаются string-backed.

S2.03d повторно использует этот dialog boundary без новых custom API и переносит
`PrefScreenView`. Activity только размещает один Fragment при первом создании;
listener и render-mode fallback принадлежат lifecycle Fragment. Три list
preferences получают штатный simple summary provider, а три slider preferences
сохраняют исходные string defaults, ranges, logarithmic/step и percent display.
Экран получает уже проверенный на API 24 `SettingsTheme`; keyboard rendering и
сам render-mode preference contract не изменяются.

S2.03e переносит пустой XML container и программно создаваемые language
checkboxes в один `PreferenceFragmentCompat`. Checkbox widgets явно
неперсистентны и не имеют keys: единственным writer остаётся consolidated
string `selected_languages`, записываемый при pause с прежним trailing comma и
empty/null behavior. Старый fallback отсутствующего полного locale, например
`en_US` → `en`, сохраняется; `input_language`, capability sets, списки layout и
dictionary probing не меняются. FragmentManager восстанавливает один Fragment
после recreation, а экран использует тот же API 24-compatible `SettingsTheme`.

S2.03f переносит `LatinIMESettings` и главный `prefs.xml`. Activity остаётся
только host одного `SeekBarPreferenceFragmentCompat`, а listener, динамические
summaries, AutoText-зависимое скрытие quick fixes, compact-mode filtering и
version/input-connection info принадлежат Fragment lifecycle. Восемь legacy
auto-summary list widgets заменены стандартными `ListPreference` с simple
summary provider, edit widget получает summary из сохранённого текста, а три
string-backed seek bar используют уже протестированный compat dialog boundary.
Четыре nested intent action, 66/48 settings contract и отсутствие eager rewrite
сохраняются. `LatinIME` принимает общий `Activity` subtype для запуска settings,
не меняя явный target class или launch flags.

S2.03g завершает миграцию без изменения settings behavior. `LatinIME`,
`KeyboardSwitcher` и `LanguageSwitcher` используют AndroidX
`PreferenceManager`, который сохраняет прежнее имя default SharedPreferences
`${applicationId}_preferences`. Пять недостижимых platform widget classes
удаляются после переноса всех XML consumers: `AutoSummaryEditTextPreference`,
`AutoSummaryListPreference`, `SeekBarPreference`, `SeekBarPreferenceString` и
`VibratePreference`. Быстрый AndroidX gate запрещает `android.preference`,
`PreferenceActivity`, возврат удалённых source files и отсутствие AndroidX
import у runtime readers. Пять устаревших `ExportedPreferenceActivity` entries
удаляются из lint baseline; оставшиеся manifest/exported изменения по-прежнему
относятся к S2.04.

XML можно переводить по одному экрану, но нельзя одновременно переименовывать
ключи, менять defaults, реструктурировать весь settings UX или внедрять Compose.

## Оценка upstream и форков

| Источник | Полезное | Не переносить |
|---|---|---|
| [upstream PR #867](https://github.com/klausw/hackerskeyboard/pull/867), head `13f4c54` | подтверждает два notification imports и AndroidX Test runner | устаревшие versions, `minSdk`/`targetSdk` change, необоснованный Jetifier, wrapper и cleanup в одном PR |
| [upstream PR #989](https://github.com/klausw/hackerskeyboard/pull/989), head `54d39f6` | удаляет AppCompat и оставляет Core/Test | target 35, PendingIntent behavior, toolchain, app defaults и version change смешаны с миграцией |
| [upstream issue #939](https://github.com/klausw/hackerskeyboard/issues/939), `SeventhM/workingBranch` @ `f194e0b` | подтверждает `FragmentActivity` + `PreferenceFragmentCompat` и отдельный dialog boundary для custom seek bar | не копировать `abd51f5`: широкий mixed diff, deprecated `setTargetFragment`, live `Preference` внутри Fragment и пометка автора `Needs better solution` |
| `hongkongphoooey/master` @ `9f1d768` | подтверждает Core/Test/AppCompat mapping как checklist | AppCompat здесь также не нужен; не брать Jetifier, global Kotlin resolution, `minSdk 29`, mass formatting и platform fixes |
| `max-pulya/master` @ `981313f` | AndroidX migration отсутствует | старые Support Library dependencies |
| `crab182/master` @ `e4d7422` | AndroidX migration отсутствует | unrelated `diyRAG/` и WIP branches |

Код из этих источников в S2.01 не импортировался. S2.02 реализуется независимо
маленьким diff; ссылки используются как provenance исследования и negative
scope examples.

## Порядок следующих задач

1. Закрыть S2.01 документационным PR.
2. Завести и выполнить S2.02 с dependency/import migration и полной CI matrix.
3. Выполнить декомпозированный S2.03: #59 фиксирует characterization contract,
   #61 переносит actions, #63 — feedback/custom dialogs, #65 — view, #70 —
   dynamic languages, #72 — main settings, #74 — runtime readers и финальное
   удаление platform API.
4. После зелёных S2.02/S2.03 перейти к platform/API issues S2.04–S2.12.
5. Повышать `targetSdk` только отдельными checkpoints S2.13–S2.18.
