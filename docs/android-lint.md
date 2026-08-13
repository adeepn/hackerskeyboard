# Android lint

## Обязательный gate

Модуль `app` запускает `:app:lintDebug` с Android Gradle Plugin 9.1.1. В
`app/build.gradle` включены `abortOnError` и `warningsAsErrors`, поэтому любая
новая lint-проблема останавливает проверку. Текущий исторический долг перечислен
точечно в `app/lint-baseline.xml`; глобальных suppressions категорий нет.

Локальный запуск через общий runner проекта:

```sh
prek run --hook-stage manual android-lint
```

Прямой Gradle-эквивалент:

```sh
./gradlew :app:lintDebug --no-daemon --stacktrace
```

Manual stage выбран намеренно: lint требует JDK 17 и Android SDK 37 и не должен
замедлять каждый маленький commit. Он обязателен перед PR, меняющим Android
код, manifest, ресурсы или Gradle-конфигурацию. GitHub Actions запускает тот же
`prek` hook на бесплатном `ubuntu-latest` runner и всегда сохраняет lint report.

## Что исправлено до baseline

Первый прогон выявил 352 errors и 328 warnings. До создания baseline исправлены:

- 308 locale-only переводов семи tutorial-строк удалены из 44 locale-файлов;
  upstream удалил их default-версии в `d2ebeae` как мёртвый AOSP tutorial-код;
- 45 неиспользуемых hardcoded package namespace удалены из переводов, что
  устранило `ResAuto` и связанные `UnusedNamespace` findings;
- повторное объявление Android namespace удалено из `recognition_status.xml`;
- обязательная колонка SQLite читается через `getColumnIndexOrThrow`;
- намеренный measurement-pass `onDraw(null)` документирован и получил точечный
  `WrongCall` suppression.

Форк `hongkongphoooey/hackerskeyboard-fork` в commit `454a375` восстановил семь
английских default-строк, а в `9f1d768` добавил широкие category suppressions.
Первое решение расходится с upstream-удалением мёртвого tutorial, второе не
ловит новые проблемы этих категорий. Поэтому из форка использована сама находка
как исследовательский сигнал, но не реализация.

## Состав baseline

Baseline создан lint 8.13.2 после перечисленных исправлений. После механической
правки XML namespace в #37 он содержит 483 точных finding. Человекочитаемая
классификация:

| Направление | Findings | Решение |
| --- | ---: | --- |
| Переводы и locale metadata | 236 | отдельная инвентаризация переводов в [#39](https://github.com/adeepn/hackerskeyboard/issues/39) |
| Ресурсы, layout и assets | 199 | дальнейшая работа [#40](https://github.com/adeepn/hackerskeyboard/issues/40) |
| Android API, lifecycle, security и accessibility | 42 | compatibility work [#38](https://github.com/adeepn/hackerskeyboard/issues/38) |
| Gradle/dependency version hints | 4 | обновлять отдельными Dependabot/toolchain PR |
| Legacy indentation | 2 | исправить отдельным formatting-only PR |

Самые крупные точные группы: `MissingTranslation` — 201,
`UnusedResources` — 118, `InOrMmUsage` — 30 и `Overdraw` — 21.
`UnusedResources` нельзя подавлять глобально: часть ресурсов
действительно загружается динамически, но новые мёртвые ресурсы всё равно
должны обнаруживаться.

58 `NamespaceTypo` из исторических keyboard XML устранены в
[#37](https://github.com/adeepn/hackerskeyboard/issues/37): custom attributes
сохранили URI `res-auto`, а вводящий в заблуждение префикс `android` заменён на
`app`. Prefix-independent snapshot проверяет 76 custom-resource XML, 15 667
атрибутов и их семантический SHA-256 через `prek`.

## Правила сопровождения baseline

- Не добавлять новый finding в baseline ради прохождения CI.
- Сначала исправить причину или оформить отдельную issue с воспроизведением,
  риском и владельцем долга.
- После исправления legacy finding удалить только соответствующую запись,
  затем прогнать lint и проверить отсутствие stale baseline entries.
- Полная регенерация разрешена только отдельным reviewable commit. Команда:

```sh
./gradlew :app:lintDebug -Dlint.baselines.continue=true --no-daemon --stacktrace
```

- Сгенерированный XML не редактировать форматтером и не заменять широким
  `lint.xml` suppression без отдельного архитектурного решения.

Официальная документация: [Android lint](https://developer.android.com/studio/write/lint)
и [prek manual stage](https://prek.j178.dev/reference/configuration/#supported-git-hook-stages).
