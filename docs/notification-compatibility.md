# Notification intents и runtime receivers

S2.05/S2.06: [#85](https://github.com/adeepn/hackerskeyboard/issues/85).
Это prerequisite повышения target, а не весь notification UX и не готовность
публикации. `targetSdk 26`, версии, preference keys и manifest components в
этом шаге не меняются.

## Контракт

- SHOW остаётся broadcast к context-registered receiver, потому что действие
  использует token живого `LatinIME`. У такого receiver нет manifest component;
  intent явно ограничен нашим package через `setPackage`, receiver регистрируется
  `ContextCompat.RECEIVER_NOT_EXPORTED`. Новых экспортированных точек входа нет.
- Settings используют component-explicit `PendingIntent.getActivity` к
  `LatinIMESettings`. Receiver больше не открывает Activity: notification
  trampoline исключён. Activity остаётся `exported=false`.
- Оба PendingIntent — `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, с разными request
  codes. Никакие fill-in данные отправителя не должны менять SHOW.
- Package-change и ringer-mode broadcasts приходят от system UID; оба listeners
  — `RECEIVER_NOT_EXPORTED`. Сохраняются три package actions, data scheme
  `package` и `RINGER_MODE_CHANGED_ACTION`. Это не универсальная политика для
  Bluetooth/telephony broadcasts от других privileged UID.
- SHOW сохраняет `showSoftInputFromInputMethod` с прежним token и `SHOW_FORCED`.
  Нельзя объявлять исправленным показ без активного editor только на основании
  успешной доставки broadcast.

## Проверки

`prek run --all-files` запускает source contract и mutation tests. Контракт
сначала был проверен на старой реализации и обнаружил отсутствие flags,
ограничения package, прямого settings entry point и receiver policy. После
исправления он проходит. Это узкий structural guard, не Java parser и не замена
device-тестам.

`NotificationActionsTest` входит в существующую API 24/API 37 instrumentation
matrix: доставка SHOW через настоящий PendingIntent, запрет fill-in extras,
явный package, immutable status на API 31+, прямое открытие private settings
activity из foreground app и безопасное игнорирование null/чужих actions.
Локальный запуск при установленном JDK 21/SDK:

```sh
prek run --all-files
prek run --hook-stage manual android-lint
scripts/run-api24-connected-tests.sh
./gradlew :app:pixel2Api37DebugAndroidTest --no-daemon \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Device-тест settings проверяет маршрутизацию, не имитирует privilege SystemUI.
Перед target checkpoint дополнительно вручную проверить:

1. Включить IME и permanent notification, нажать SHOW с активным editor.
2. Нажать Settings в уведомлении из другого приложения; проверить открытие
   настроек и возврат назад.
3. Выключить/включить уведомление, пересоздать IME; проверить unregister и
   отсутствие дублирующих callbacks.
4. Установить/удалить словарь; проверить обновление списка плагинов.
5. Изменить режим звонка; проверить feedback, не меняя системную громкость из
   автоматических тестов.
6. Отдельно зафиксировать SHOW без editor на OnePlus/Android 16: известное
   мигание клавиатуры остаётся отдельным compatibility investigation.

Notification permission/denial UX относится к S2.08. Полные cross-UID attack
tests и package/ringer system-event tests этим шагом не реализованы.

## Источники

Upstream [#897](https://github.com/klausw/hackerskeyboard/issues/897) и
[#947](https://github.com/klausw/hackerskeyboard/issues/947) — evidence поведения
SHOW на Android 12/14, не готовые патчи.
[#515](https://github.com/klausw/hackerskeyboard/pull/515) — исторический fix
уведомления для Android M, не решение современных target restrictions.
Код из upstream/форков не импортировался.

Официальные основания:
[Android 12 notification trampolines и mutability](https://developer.android.com/about/versions/12/behavior-changes-12),
[runtime receiver policy](https://developer.android.com/develop/background-work/background-tasks/broadcasts).
