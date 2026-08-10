# Исследование форков

Дата исследования: 10 августа 2026 года. Сравнение выполнено по фактически
полученным Git refs, а не только по README форков.

## Подключённые remotes

| Remote | Repository | Исследованная ветка/tip |
|---|---|---|
| `max-pulya` | https://github.com/max-pulya/hackers_keyboard_by_max_pulya | `master` @ `981313f` (2026-07-17) |
| `crab182` | https://github.com/crab182/hackerskeyboard | `master` @ `e4d7422` (2026-07-09), также остальные ветки |
| `hongkongphoooey` | https://github.com/hongkongphoooey/hackerskeyboard-fork | `master` @ `9f1d768` (2026-06-24) |
| `upstream` | https://github.com/klausw/hackerskeyboard | canonical repository; issues/PR исследуются отдельно |

Обновление данных:

```sh
git fetch --prune max-pulya
git fetch --prune crab182
git fetch --prune hongkongphoooey
git fetch --prune upstream
```

Remote configuration хранится в локальном `.git/config` и не переносится через
обычный commit; таблица выше является канонической инструкцией для нового clone.

## Сводный вывод

| Fork | Android modernization | Полезные функции | Можно merge целиком? |
|---|---|---|---|
| max-pulya | Частичная, до target 30 | Да, много Android 15 fixes и power-user features | Нет |
| crab182 | Нет новых Android-изменений относительно нашей базы | WIP transparent candidates старше базы | Нет |
| hongkongphoooey | Черновая миграция до target 34/AndroidX | В основном compatibility fixes | Нет |

Наиболее полезная стратегия: взять `hongkongphoooey` как checklist platform API,
а `max-pulya` как набор отдельных behavior fixes/feature proposals. Каждый патч
нужно переносить поверх чистой архитектуры v2 с тестом, не cherry-pick сериями.

## max-pulya/hackers_keyboard_by_max_pulya

### История и масштаб

Fork основан непосредственно на нашем текущем `master` (`9202d9d`) и содержит
35 последующих коммитов: 31 файл, примерно 2 870 добавлений и 253 удаления. Это
самый содержательный функциональный форк.

Сборка была поднята до AGP 8.7/Gradle 8.9, добавлен wrapper и `namespace`, но:

- `compileSdk 30`, `targetSdk 30`, `minSdk 21`;
- остались `jcenter()` и старая Support Library;
- release помечен `debuggable true`;
- target не удовлетворяет современным требованиям;
- manifest не исправляет обязательный `android:exported`;
- для словарей добавлен `QUERY_ALL_PACKAGES` вместо узких `<queries>`;
- добавлен `SYSTEM_ALERT_WINDOW`, что требует отдельного обоснования и UX;
- код содержит debug `System.out.println` и крупные изменения в монолитном
  `LatinIME`.

### Кандидаты высокой ценности

| Commit | Изменение | Решение для v2 |
|---|---|---|
| `8155220` | candidates bar на новых Android | Высокий приоритет: воспроизвести bug, изучить геометрию и адаптировать |
| `613ea60` | ширина landscape keyboard | Высокий приоритет: добавить geometry regression test и портировать минимальный fix |
| `0b6af30` | символы верхнего ряда русской ЙЦУКЕН | Проверить с layout golden tests и адаптировать |
| `ca0b260` | Fn ошибочно вводит запятую | Высокий приоритет после modifier test |
| `0fffdba`, `981313f` | indicators/modifiers при смене языка | Полезно, но переносить через явную modifier state machine |
| `cb6b388` | Ctrl+Backspace | Полезная opt-in функция; проверить semantics разных editors |
| `19f9c61`, `70959c7` | запуск/показ через notification | Использовать как поведенческую подсказку, но реализовать modern explicit intents самостоятельно |
| `c556fd4`, `73de35b` | большой PIN/phone layout | Рассмотреть после базовой parity как отдельную настройку |
| `be363de`, `4442403` | смешанные en/ru password layouts | Интересная opt-in feature; обязательна privacy/password проверка |
| `dacb1a2`, `7a0dd78` | virtual gamepad/D-pad | Отложить до v2.x; требует UX, overlay и accessibility review |

### Не переносить напрямую

- `6dea658`: полезные build и modifier идеи смешаны с большим behavior diff;
- `8b69285`/`e009fa4`: `QUERY_ALL_PACKAGES` слишком широк и может создать
  проблемы Google Play; использовать `<queries>` из другого форка;
- `b9dc62e`/`cadd8ab`: инфраструктура app-specific suggestions интересна, но
  текущая реализация содержит hard-coded package names, DNS providers, AI
  prompts, browser domains и команду `/del`. Если функция нужна, проектировать
  пользовательские snippets/actions без чтения/профилирования приложений;
- settings Tab hack с DNS, включённый по package name;
- `SYSTEM_ALERT_WINDOW` и gamepad overlay без отдельного product/security design;
- изменение default numeric row и hints без migration/product decision;
- wrapper/build files как финальное решение: версии уже недостаточны для цели.

### Итог

Fork ценен прежде всего живым тестированием на Android 15 и исправлениями
модификаторов, candidates и landscape. Его код нельзя считать автоматически
корректным: коммиты часто смешивают функции, обходы и отладочный код. Для этапа 3
следует создать отдельный набор `max-pulya regression cases`, затем адаптировать
выбранные исправления.

## hongkongphoooey/hackerskeyboard-fork

### История и масштаб

Fork основан непосредственно на нашем `master` и содержит шесть коммитов.
Заявленная цель — `minSdk 29`, `targetSdk 34`. Из-за массового форматирования
155 файлов итоговый diff выглядит как 17 762 добавления и 17 510 удалений, хотя
meaningful Java/build diff без whitespace составляет порядка нескольких сотен
строк.

### Полезные modernization changes

- `compileSdk/targetSdk 34`, `namespace` и удаление manifest `package`;
- `jcenter()` → `mavenCentral()`;
- Support Library → AndroidX AppCompat/Core/Test;
- manifest `POST_NOTIFICATIONS`, `<queries>` для двух dictionary contracts и
  `android:exported`;
- `PendingIntent.FLAG_IMMUTABLE`;
- runtime receiver flags на API 33+;
- новые PackageManager flags overloads;
- `VibrationEffect`;
- замена ряда устаревших API и предупреждений;
- JUnit/test dependencies обновлены.

Это хороший checklist для этапа 2. Особенно полезны узкие `<queries>` и изменения
в `PluginManager`, которые предпочтительнее `QUERY_ALL_PACKAGES` из max-pulya.

### Проблемы и ограничения

- AGP 8.1/target 34 уже ниже цели API 36;
- `minSdk` без необходимости поднят с 14 до 29, исключая старые устройства и
  позволяя скрыть compatibility work;
- `gradlew` изменён, но `gradle/wrapper/gradle-wrapper.jar` и properties в tip не
  добавлены: сборка нового clone не воспроизводима;
- `android.preference` и старый voice JAR всё ещё остаются;
- добавлен `POST_NOTIFICATIONS`, но не виден полноценный runtime permission flow;
- несколько settings activities экспортированы наружу, хотя можно выбрать более
  узкую модель;
- receivers для package changes требуют отдельной проверки: system-only
  broadcasts нельзя механически помечать тем же флагом, что internal receiver;
- часть notification action всё ещё использует implicit broadcasts;
- Kotlin dependency conflict подавлен глобальной принудительной версией вместо
  устранения причины;
- release lint в основном подавлен через `lint.xml`;
- большая часть последнего коммита — механическое переформатирование XML;
- обнаружены подозрительные перемещения Hebrew resources во вложенные каталоги
  (`values-iw/values-he`, `xml-iw/xml-he`), которые нельзя принимать без сборки и
  проверки resource resolution;
- README не документирует migration или результаты device tests.

### Решение по коммитам

| Commit | Решение |
|---|---|
| `3ab0b18` | Не cherry-pick. Использовать как checklist и портировать узкие hunks с тестами |
| `0205519` | Не использовать: wrapper остаётся неполным |
| `988f87c` | Пересмотреть после lint baseline; не смешивать с API migration |
| `454a375` | Default strings проверить выборочно; global Kotlin resolution strategy не брать |
| `9f1d768` | Не брать: mass formatting скрывает изменения и содержит сомнительные resource moves |

### Итог

Fork подтверждает, что legacy Java можно адаптировать без полного rewrite, но не
является готовой основой v2. Полезные API patterns нужно реализовать заново на
API 36, сохраняя более низкий `minSdk` и добавляя тесты.

## crab182/hackerskeyboard

### Состояние

`master` расходится с нашей веткой от коммита 2019 года, но его 2026 additions —
целиком посторонний каталог `diyRAG/` (Rust/Python self-hosted RAG platform): 159
файлов и около 27 649 строк. Android keyboard files относительно точки
расхождения не менялись. Это похоже на случайное использование репозитория для
несвязанного проекта.

Ветки:

- `master` и `claude/exciting-franklin-99vuda` указывают на один contaminated
  tip;
- `gingerbread-aosp` — исходный импорт AOSP 2011 года;
- `wip-transp-candidates` — два WIP-коммита 2018 года, причём commit message прямо
  говорит, что реализация работает неправильно и мешает выйти из settings.

### Решение

- ничего не merge/cherry-pick из `master` или Claude branch;
- не импортировать `diyRAG/`;
- transparent candidates WIP можно использовать только как историческую идею,
  но не как код;
- старые tags в основном отражают общую историю Hacker's Keyboard и уже входят
  в ancestry нашей базы, поэтому дополнительной модернизации не дают.

## Очередь исследования и портирования

1. На этапе 2 адаптировать из `hongkongphoooey` manifest/package visibility,
   PendingIntent, receiver и modern PackageManager patterns — вручную для API 36.
2. На этапе 3 воспроизвести и покрыть тестами `max-pulya` fixes:
   candidates → landscape width → Fn comma → modifier indicators/language.
3. После parity отдельно рассмотреть Ctrl+Backspace и mixed en/ru layouts.
4. В v2.x спроектировать generic snippets/actions вместо hard-coded suggestions.
5. Gamepad/overlay рассматривать только после ADR по permissions, accessibility,
   product scope и Play policy.

## Команды для повторного анализа

```sh
git log --no-merges master..max-pulya/master
git diff --stat master..max-pulya/master
git diff --ignore-all-space master..hongkongphoooey/master -- app build.gradle gradle.properties
git diff --name-status "$(git merge-base master crab182/master)"..crab182/master
```

При последующих fetch этот документ нужно обновлять, если tip одного из форков
изменился.

Issues и PR канонического проекта разобраны отдельно в `upstream-research.md`,
поскольку они являются не одним fork snapshot, а постоянно меняющимся backlog.
