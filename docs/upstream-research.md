# Исследование canonical upstream issues и PR

Источник: [klausw/hackerskeyboard](https://github.com/klausw/hackerskeyboard).
Первичный срез выполнен 10 августа 2026 года через GitHub API. Это живой backlog:
перед реализацией связанной задачи поиск нужно повторять.

Локальный remote:

```text
upstream https://github.com/klausw/hackerskeyboard.git
```

Обновление веток:

```sh
git fetch --prune upstream
```

Issues и PR не загружаются обычным `git fetch`; они исследуются через GitHub API
и ссылки из этого документа.

## Главный вывод

Upstream содержит полезный объём уже выполненной работы, особенно PR
[#978](https://github.com/klausw/hackerskeyboard/pull/978),
[#989](https://github.com/klausw/hackerskeyboard/pull/989) и
[#980](https://github.com/klausw/hackerskeyboard/pull/980). Они подтверждают,
что проект можно довести до API 36 без полного rewrite. Ни один из них нельзя
переносить целиком: изменения смешивают toolchain, platform behavior, ресурсы,
функции и массовые преобразования, а для #978 уже сообщены runtime-регрессии.

## Приоритетные pull requests

### PR #978 — SDK 21–36

Статус на момент исследования: open, 52 commits, 144 changed files, около 17,4
тыс. добавлений и 17,2 тыс. удалений. Head `14a97ade`, mergeable по данным
GitHub.

Полезные части:

- полный Gradle wrapper;
- `compileSdk/targetSdk 36`, `minSdk 21`;
- AGP 8.6.1, AndroidX и `mavenCentral()`;
- manifest/exported, pending intents, receiver APIs;
- dictionary package visibility;
- candidates/layout/Android 16 insets changes;
- сборка release artifacts;
- опыт реального использования Android 15/16.

Почему не cherry-pick/merge:

- 144 файла и массовое форматирование ресурсов скрывают semantic diff;
- toolchain уже отличается от нашего целевого AGP 8.13/JDK 17;
- CI ориентирован на tag release, Java 21 и содержит signing workflow, но не PR
  test gates;
- lint `NotificationPermission` подавлен вместо полноценного UX;
- manifest/export choices требуют отдельной security-проверки;
- подписывание встроено прямо в application build config;
- dictionary README ссылается на сторонний APK source, что нельзя принимать без
  supply-chain анализа;
- пользователь подтвердил необходимость переустановки APK и потерю настроек —
  нужно проверить signing/application compatibility;
- в обсуждении сообщены navigation bar overlap, случайное появление клавиатуры
  поверх launcher/Firefox и несогласованное изменение размеров окна.

Особенно важная отрицательная находка: попытка исправлять Android 16 insets
через `fitsSystemWindows` должна быть проверена отдельной матрицей. Build success
не доказывает корректность IME window lifecycle.

Решение: issue S1.19 декомпозирует PR по коммитам. Сначала можно использовать
wrapper/build metadata как reference; platform hunks портируются только после
соответствующего regression test. Navigation bar и spontaneous-show reports
становятся обязательными negative cases S2.22.

### PR #989 — компактная модернизация build setup

Статус: open, 2 commits, 13 files, target SDK 35, проверен автором только через
`assembleDebug`.

Полезные части:

- небольшой и обозримый набор build/wrapper/CMake изменений;
- AndroidX notifications и exported declarations;
- modern wrapper как дополнительный reference;
- CMake fixes можно сравнить с #978 и современным NDK.

Ограничения:

- target 35, не 36;
- меняет default keyboard mode и version на v2.0 в build PR;
- нет lint, release, tests, device/API и 16 KB evidence;
- версии toolchain нужно перепроверить и обновить.

Решение: использовать #989 как более чистую контрольную реализацию S1.01–S1.09,
но не cherry-pick целиком. Изменение default layout исключить из migration PR.

### PR #980 — «comprehensive unit tests»

Статус: closed without merge, 7 commits, 72 files, около 18,6 тыс. добавлений.

Заявлены тесты для WordComposer, TextEntryState, ModifierKeyState, SwipeTracker,
EditingUtil, Suggest, ExpandableDictionary, LanguageSwitcher и Dictionary. Это
точно совпадает с нашей стратегией этапа 3.

Проблема: тесты смешаны с почти полной автоматической Java→Kotlin конвертацией,
дублирующими source sets и тысячами строк. Поэтому объём не соответствует
названию PR, а успешность тестов нельзя считать доказанной для нашего Java code.

Решение: в S1.21/S3.26 изучить каждый test file как specification, перенести
небольшими независимыми PR на исходный Java и проверить, что assertions реально
ловят регрессии. Kotlin production code и дублированные classes не брать.

### PR #867 — AndroidX

Статус: open с 2021 года, 4 commits, 11 files. Исторически показывает ранний
AndroidX путь, но давно устарел относительно #978/#989. В обсуждении есть
неподтверждённое сообщение «does not type well anymore» и нет современных
device tests.

Решение: reference второго порядка. Использовать только для сравнения истории;
не строить на нём этап 2.

### PR #952 — emoji keyboard

Статус: закрыт без merge; 125 files и массовый resource diff ради относительно
небольшой функции.

Решение: не переносить. Требование emoji зафиксировано issue
[#765](https://github.com/klausw/hackerskeyboard/issues/765), но новая функция
должна проектироваться после compatibility release, небольшим opt-in PR и без
массового форматирования раскладок.

## Приоритетные группы issues

### P0: совместимость и регрессии современных Android

| Issues | Наблюдение | Действие v2 |
|---|---|---|
| [#957](https://github.com/klausw/hackerskeyboard/issues/957) | Android 15 обрезает landscape справа | Объединить с проверкой max-pulya `613ea60`; geometry test до fix |
| [#964](https://github.com/klausw/hackerskeyboard/issues/964), [#901](https://github.com/klausw/hackerskeyboard/issues/901) | пустая candidates bar/нет suggestions на Android 13/15 | Объединить с max-pulya `8155220`; candidates lifecycle и screenshot tests |
| [#883](https://github.com/klausw/hackerskeyboard/issues/883), [#897](https://github.com/klausw/hackerskeyboard/issues/897), [#947](https://github.com/klausw/hackerskeyboard/issues/947) | постоянное уведомление не показывает IME на Android 12/14 | Не обещать показать IME без focused editor; проверить современный допустимый UX и accessibility scenario |
| [#958](https://github.com/klausw/hackerskeyboard/issues/958) | приложение недоступно новым устройствам в Play | Закрывается target 36/release readiness, но требует signing/update-path проверки |
| [#855](https://github.com/klausw/hackerskeyboard/issues/855) | force close Android 11 | Получить stack trace/reproduction; включить в API matrix |
| [#686](https://github.com/klausw/hackerskeyboard/issues/686) | popup работает неправильно | Popup edge/shift/multitouch regression suite |
| [#990](https://github.com/klausw/hackerskeyboard/issues/990) | auto-capitalization зависит от editor/WPS | Добавить `EditorInfo` и InputConnection interoperability case |

### P1: ключевая функциональность power-user keyboard

| Issues | Значение для плана |
|---|---|
| [#452](https://github.com/klausw/hackerskeyboard/issues/452), [#460](https://github.com/klausw/hackerskeyboard/issues/460), [#815](https://github.com/klausw/hackerskeyboard/issues/815) | modifier chording и double-press — обязательная test matrix этапа 3 |
| [#899](https://github.com/klausw/hackerskeyboard/issues/899) | unwanted uppercase — связано с modifier/language fixes max-pulya |
| [#918](https://github.com/klausw/hackerskeyboard/issues/918), [#929](https://github.com/klausw/hackerskeyboard/issues/929) | landscape key/extension row regressions |
| [#945](https://github.com/klausw/hackerskeyboard/issues/945), [#555](https://github.com/klausw/hackerskeyboard/issues/555) | dictionary availability вне Play и package discovery |
| [#979](https://github.com/klausw/hackerskeyboard/issues/979), [#965](https://github.com/klausw/hackerskeyboard/issues/965) | legacy Google voice lock-in и расположение mic key; учитывать при замене voice JAR |

### P2: развитие после v2 compatibility release

- emoji: #765 и PR #952;
- numpad: [#987](https://github.com/klausw/hackerskeyboard/issues/987);
- дополнительные символы: [#988](https://github.com/klausw/hackerskeyboard/issues/988);
- clipboard: [#932](https://github.com/klausw/hackerskeyboard/issues/932);
- configurable layouts: [#13](https://github.com/klausw/hackerskeyboard/issues/13);
- on-device speech alternatives: [#971](https://github.com/klausw/hackerskeyboard/issues/971).

Эти задачи не должны задерживать build/compatibility/test milestones.

## Security reports and untrusted contributions

Issue [#986](https://github.com/klausw/hackerskeyboard/issues/986) содержит
AI-generated security report и ZIP attachment. Это потенциально полезный сигнал,
но не проверенный patch source. Архив нельзя распаковывать или запускать в
рабочем checkout. Нужна отдельная security research issue: получить список
claims в изолированной среде, независимо воспроизвести каждый claim на нашем
коде и реализовать минимальные fixes с тестами.

PR с большим AI-generated diff, отсутствием тестов или несоответствием title и
scope оценивается так же строго независимо от автора.

## Регулярный upstream triage

Перед каждым milestone planning и минимум перед alpha/beta/release:

1. обновить `upstream` remote;
2. получить open PR, recent closed PR и issues, обновлённые после прошлого среза;
3. сгруппировать дубликаты по behavior/API;
4. связать их с нашими issues;
5. проверить новые patches, device reports и regression comments;
6. обновить этот документ и test matrix;
7. не закрывать upstream issues от имени проекта без согласованной коммуникации.

## Очередь ближайшего анализа

1. Сравнить wrapper/build/CMake из #978 и #989 с целевыми AGP 8.13/Gradle 8.13.
2. Разобрать 52 commits #978, отделив build-only от runtime behavior.
3. Вынести regressions из обсуждения #978 в автоматизируемые scenarios.
4. Проверить тесты #980 по одному, начиная с ModifierKeyState и TextEntryState.
5. Объединить #957/#964 с соответствующими max-pulya fixes и написать failing
   tests до переноса кода.
