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

Notification permission/denial UX относится к S2.08b. Полные cross-UID attack
tests и package/ringer system-event tests этим шагом не реализованы.

## S2.08a — lifecycle перед permission UX

[#89](https://github.com/adeepn/hackerskeyboard/issues/89) разделён на два
reviewable PR. В первой части повторный `setNotification(true)` больше не
отменяет уведомление и не снимает SHOW receiver. `setNotification(false)`
всегда отменяет notification ID, даже без живого receiver; unregister происходит
только один раз. `onDestroy()` вызывает тот же cleanup до teardown IME.
Channel ID, notification ID, intents, preference key/default и target 26
сохраняются.

`NotificationLifecycleTest` на main thread создаёт service с target context,
но не запускает IME session. Он выполняет настоящий private notification path
через reflection, проверяет enable → enable → disable → disable → enable и
identity receiver. Это проверка регистрации/очистки, а не выдачи permission,
отображения SystemUI или полного framework-driven `onDestroy`. Source/mutation
guard дополнительно фиксирует вызов cleanup из `onDestroy` и ловит возврат
ошибочной ветки. До исправления source guard красный по обеим новым проверкам.

Ручной regression case: включить уведомление, переключиться на другую IME,
дождаться уничтожения старого service; старого SHOW notification быть не должно.
При возвращении к Hacker's Keyboard уведомление появляется снова, если
настройка включена. Повторная доставка одного и того же preference update не
должна его скрывать. Проверить на API 24 и OnePlus 13 / Android 16.

S2.08a выполнен в PR #90. Эта первая часть сама по себе не исправляет permission
denial или SHOW без editor.

## S2.08b — optional notification permission

Manifest объявляет `POST_NOTIFICATIONS` только ради опционального уведомления.
`keyboard_notification` сохраняет прежние key, boolean type и default: это
желание пользователя, а не копия Android permission. Отказ не сбрасывает его,
не отключает IME и не меняет input preferences.

- `NotificationAccess` проверяет runtime permission на API 33+, app-wide block
  и importance существующего канала `PCKeyboard`. Пока нет разрешения, service
  не создаёт канал и не регистрирует SHOW receiver; существующий notification
  отменяется. Между проверкой и `notify` возможен revoke: узкий catch очищает
  notification/receiver, не прерывая IME.
- Включение checkbox — единственная автоматическая точка запроса. На устройстве
  и target >=33 используется Activity Result permission contract; rationale
  после предыдущего отказа — восстанавливаемый child DialogFragment. Grant,
  denial и dismissal лишь обновляют отображение/notification state, не запускают
  повторный запрос. На target <33 используется системная страница уведомлений:
  приложение ещё не управляет временем OS dialog.
- При блокировке checkbox остаётся включённым, summary прямо сообщает о запрете
  Android. Дополнительная неперсистентная action «Доступ к уведомлениям» ведёт в
  системные настройки, в том числе после permanent denial или block канала.
  На API 24–25 открывается app details; при отсутствии notification settings
  activity используется тот же fallback, затем понятное сообщение.
- Возврат в settings (`onResume`) и permission callback обновляют summary и
  отправляют package-scoped REFRESH_NOTIFICATION без extras. Уже существующий
  private runtime receiver IME читает сохранённое желание, не доверяя данным
  broadcast. Новых exported components и static service references нет.
- IME перепроверяет доступ при начале input view. Ни этот путь, ни создание
  service, ни простое открытие settings не вызывают permission launcher.

Точечный `MissingPermission` suppression у `setNotification` сохранён: lint не
прослеживает объединённую проверку helper. Проверки permission/app/channel,
границы SDK и cleanup защищены source/mutation tests. Глобальных suppressions
или новых baseline entries нет.

### Проверки и границы

`NotificationPermissionPolicyTest` — JVM matrix device/target API, permission,
app-wide и channel block. Device tests на API 24/37 проверяют manifest и settings
intent, synthetic runtime denial через ContextWrapper, реальный app-op deny →
allow, отсутствие SHOW receiver при запрете, повторное enable после разрешения,
blocked summary и сохранение checkbox при `Activity.recreate()`. Rationale и
настоящий runtime permission dialog под target >=33 проверяются на target 33
checkpoint отдельно: текущий APK всё ещё target 26.

Instrumentation выполняется **только на disposable test installation**: fixture
выдаёт POST_NOTIFICATIONS test target на API 33+ и временно меняет его app-op
POST_NOTIFICATION. В finally app-op возвращается в allow, preference восстанавливается;
сам runtime grant сохраняется до удаления тестового APK. Это не revoke/grant
реального пользовательского приложения и не доказательство прохождения OS dialog.
Полный InputConnection typing test при denial и SystemUI taps остаётся ручным.

Перед target 33 checkpoint обязательно выполнить на API 33+ и OnePlus/Android 16:

1. Чистая установка, настройка выключена: ввод работает, permission dialog не
   появляется при старте IME или открытии settings.
2. Включить уведомление → Allow: оно появляется, SHOW/settings работают.
3. Deny или закрыть dialog: ввод продолжает работать; preference сохраняется,
   виден blocked summary; возврат/recreation не повторяет prompt.
4. Повторное включение после отказа: rationale, Cancel, rotation и Allow;
   не более одного dialog, без автоматического retry.
5. Permanent denial, app-wide block и отдельно block `PCKeyboard`: action
   открывает системные настройки; после разрешения/возврата notification
   восстанавливается. Если checkbox выключен, он не появляется.
6. Снять разрешение, пока IME жив: следующая input session не падает и не
   оставляет SHOW receiver. Проверить обычный ввод и terminal modifiers.

Основание:
[Android notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission),
[runtime permission flow](https://developer.android.com/training/permissions/requesting).

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
