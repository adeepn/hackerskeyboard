# Видимость dictionary packs — S2.07

Задача: [#87](https://github.com/adeepn/hackerskeyboard/issues/87).
После повышения target до 30+ Android фильтрует результаты PackageManager.
`PluginManager` должен продолжить находить установленные словари по двум
существующим протоколам, независимо от нового applicationId клавиатуры.

| Протокол | Запрос существующего кода | Manifest query |
|---|---|---|
| Hacker's Keyboard | `queryIntentActivities` | `org.pocketworkstation.DICT` |
| AnySoftKeyboard | `queryBroadcastReceivers` с metadata | `com.menny.android.anysoftkeyboard.DICTIONARY` |

Manifest содержит ровно эти две action-only `<queries><intent>`. Не добавляются
`QUERY_ALL_PACKAGES`, общий MAIN/LAUNCHER query, список известных package names
или provider grants. Категория DEFAULT тоже не нужна: она сузила бы поиск
относительно существующих action-only запросов. При добавлении новых queries
контракт и тесты меняются отдельным reviewed PR.

`PluginManager`, форматы/ресурсы словарей, приоритет HK над ASK, locale fallback
и JNI остаются без изменений. Старый action `org.pocketworkstation.DICT` не
переименовывается в `com.baodeep.*`: он является внешним API. Воспроизводимые
first-party dictionary packs остаются в #27, кнопка поиска издателя — в #69.
Voice queries будут рассмотрены вместе с заменой legacy voice JAR в S2.09.

## Автоматические проверки

- Быстрый verifier сначала воспроизвёл отсутствие queries в старом manifest.
- `prek run --all-files` включает source contract и пять тестов с мутациями:
  удаление/переименование/дублирование action, широкие permissions в двух
  XML-формах, дополнительные package/provider/intent queries, лишняя category.
- `scripts/verify-apk-identity.sh` проверяет тот же контракт в decoded manifests
  debug/release APK; `verify-release-bundle.py` — в release AAB. Это обнаруживает
  и разрешения/queries, добавленные зависимостями при manifest merge.
- `PackageVisibilityTest` в существующей API 24/API 37 matrix читает manifest
  установленного приложения и проверяет оба action и отсутствие broad permission.

Локальные команды после сборки APK/AAB при наличии проектных JDK 21/SDK:

```sh
prek run --all-files
scripts/verify-apk-identity.sh \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
prek run --all-files --hook-stage manual release-bundle
```

Исходный/decoded XML можно проверить отдельно:
`python3 scripts/verify_package_visibility.py` или передать decoded manifest
через stdin с `--stdin`.

## Что пока не проверено: реальная фильтрация

В этом PR `targetSdk` остаётся 26. Поэтому успешный device smoke **не доказывает**
обнаружение сторонних пакетов под package visibility filtering. Installed
manifest test проверяет упаковку, не discovery. До принятия target 31 checkpoint
нужен отдельный положительный и отрицательный integration test:

1. На чистом API 30+ emulator установить build с target >=30, HK activity pack,
   ASK receiver pack и независимый пакет без dictionary intent filter.
2. Fixtures не должны быть instrumentation APK, частью target APK, иметь общий
   UID или `forceQueryable`; не открывать их до проверки, чтобы не получить
   автоматическую видимость из-за взаимодействия.
3. Из UID клавиатуры проверить `queryIntentActivities` и
   `queryBroadcastReceivers`, доступ к metadata/resources, загрузку обоих
   форматов и positive/negative JNI lookup.
4. Контрольный посторонний пакет должен оставаться невидимым. В тестовом варианте
   без соответствующего query dictionary fixture тоже должен исчезать из
   результатов: это проверяет, что тест действительно зависит от `<queries>`.
5. Проверить install/remove/replace во время жизни IME и отсутствие устаревшего
   dictionary cache. Вернуть все тестовые настройки после запуска.

Этот сценарий пока не выполнен и не заменяется наличием APK на Android 17.
Изменение manifest не является подтверждением Play acceptance или исправлением
доступности словарей в магазине.

## Исследование и provenance

Upstream [#945](https://github.com/klausw/hackerskeyboard/issues/945) и
[#555](https://github.com/klausw/hackerskeyboard/issues/555) — evidence проблем
дистрибуции словарей, **не** доказательство ошибки package filtering.
Scoped upstream PR search по `queries` не дал candidate patch; внешний код
не импортировался.

Официальные основания:
[package visibility](https://developer.android.com/training/package-visibility),
[объявление intent queries](https://developer.android.com/training/package-visibility/declaring),
[синтаксис queries](https://developer.android.com/guide/topics/manifest/queries-element).
