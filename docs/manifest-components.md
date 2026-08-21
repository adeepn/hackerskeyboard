# Manifest components и `android:exported`

S2.04 ([#76](https://github.com/adeepn/hackerskeyboard/issues/76)) фиксирует
явную и минимальную политику доступности компонентов до повышения `targetSdk`.
Изменение не добавляет новый внешний API и не меняет package, подпись, UI или
поведение клавиатуры.

## Политика компонентов

| Компонент | `exported` | Обоснование |
|---|---:|---|
| `LatinIME` service | `true` | Input Method Manager другого процесса должен обнаружить и подключить IME |
| `Main` | `true` | launcher entry point с `ACTION_MAIN` / `CATEGORY_LAUNCHER` |
| `LatinIMESettings` | `false` | открывается самим приложением и привилегированными системными настройками по explicit component |
| `InputLanguageSelection` | `false` | внутренний вложенный экран настроек |
| `PrefScreenActions` | `false` | внутренний вложенный экран настроек |
| `PrefScreenView` | `false` | внутренний вложенный экран настроек |
| `PrefScreenFeedback` | `false` | внутренний вложенный экран настроек |

Экспортированный `LatinIME` не является общедоступным bind service:
`android.permission.BIND_INPUT_METHOD` сохраняется на service declaration и
выдаётся только системным компонентам. `res/xml/method.xml` продолжает объявлять
`com.baodeep.hackerskeyboard.LatinIMESettings` как settings activity IME.

## Проверяемый контракт

`scripts/verify-manifest-components.py` проверяет полный список компонентов с
intent filters, точные значения `android:exported`, IME permission/action/meta
data, launcher category и settings activity. Любой новый filtered component
должен сначала получить reviewed export policy. Mutation tests в
`scripts/test-manifest-components.py` доказывают, что gate отклоняет:

- отсутствие явного `android:exported`;
- закрытый IME service;
- экспорт внутреннего settings screen;
- удаление `BIND_INPUT_METHOD`;
- смену settings activity в IME metadata.

Instrumentation на API 24 и API 37 читает фактически установленный package
через `PackageManager`, проверяет exported flags и permission, а
`InputMethodManager` — регистрацию IME и `getSettingsActivity()`. Существующие
smoke tests запускают все settings flows из UID приложения и защищают
внутреннюю навигацию после закрытия компонентов наружу.

## Исследованные реализации

Canonical upstream PR #978 не используется: там IME service закрыт, а часть
внутренних экранов, наоборот, экспортирована. PR #989 ближе к выбранной модели и
использован только как дополнительное свидетельство; реализация и тесты v2
созданы независимо. Ветка `hongkongphoooey` с экспортом всех filtered
components отвергнута как избыточная. В `max-pulya` и `crab182` применимого
исправления нет.

Официальные основания: Android требует для IME action
`android.view.InputMethod`, metadata `android.view.im` и permission
`BIND_INPUT_METHOD`; manifest и security guidance требуют явной, максимально
узкой экспортируемости. Эти ссылки сохранены в issue #76 и должны быть повторно
проверены при изменении component inventory.
