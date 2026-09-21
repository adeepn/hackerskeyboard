# Голосовой ввод: S2.09

Tracking: [#93](https://github.com/adeepn/hackerskeyboard/issues/93).
S2.09a был принят при `targetSdk 26`. Миграция разделена на два reviewable PR, поскольку
старый JAR содержит два разных пути, а не только запуск speech Activity.

## Исследование старого контракта

Просмотр bytecode уже включённого `app/libs/voiceimeutils.jar` через `javap`
выявил:

- `ImeTrigger` ищет включённые IME с объявленным subtype `voice`, но принимает
  только package prefix `com.google.android`; переключает IME через старый
  `InputMethodManager.setInputMethodAndSubtype` и window token.
- `IntentApiTrigger` проверяет `RECOGNIZE_SPEECH`, запускает service/activity
  bridge и откладывает результат до следующего `onStartInputView`. Привязка
  результата к исходной editor session требует отдельной проверки/замены.
- `LatinIME.shouldShowVoiceButton()` пока всегда возвращает `true`.

Это характеристика бинарника, не импорт исходников и не подтверждение его
лицензионного происхождения. S1.22 licensing inventory остаётся актуальным;
новый адаптер написан независимо по публичным Android API. JAR не изменяется.

Upstream проверен 21 сентября 2026:

- [#799](https://github.com/klausw/hackerskeyboard/issues/799) — evidence:
  неработающий mic после изменений Gboard/включённых клавиатур.
- [#979](https://github.com/klausw/hackerskeyboard/issues/979) — evidence:
  ограничение на Google. Произвольное приложение с microphone permission не
  становится совместимым voice provider автоматически.
- [#937](https://github.com/klausw/hackerskeyboard/issues/937) — запрос поддержки
  FUTO, не candidate patch и не доказательство совместимости конкретной версии.
- Canonical PR search `voice` не дал результатов. Код/вложения не переносились.

## S2.09a: переключение на voice-IME

`VoiceImeSwitcher` получает свежий список **включённых** IME и их включённых
subtypes, включая implicit defaults. Выбирает первый `voice` subtype в порядке,
возвращённом системой, без Google allowlist; собственный пакет исключается.
Настройка предпочтительного провайдера/языка не добавляется. При нескольких
провайдерах порядок системы может меняться; это не выбор системного default STT.

На API 28+ используется `InputMethodService.switchInputMethod(id, subtype)`;
на API 24–27 — публичный compatibility API с token и тем же subtype. Результат
`REQUESTED` означает лишь отсутствие синхронной ошибки, не доказательство
переключения или успешного распознавания. Удаление/отключение провайдера и
`SecurityException`/`IllegalArgumentException` не должны завершать клавиатуру.
При такой ошибке показывается сообщение; другой провайдер не запускается
скрытно. English fallback и русский перевод сообщения добавлены отдельно;
остальные локали отслеживаются в #38, suppression только у нового ключа.

Только отсутствие подходящего subtype (`UNAVAILABLE`) передаёт управление
старому JAR. Это **временный legacy fallback**, который может также выбрать
Google IME по своей старой политике. Настройки `voice_mode`, размещение mic,
сохранение текста и lifecycle старого recognizer bridge пока не меняются.
Скрытие mic при отсутствии обоих маршрутов откладывается до S2.09b.

Manifest добавляет только `android.view.InputMethod` query, необходимый для
доступности IME при переключении под современной package visibility policy.
Два dictionary query сохраняются. Нет новых permissions, exported components,
сетевых запросов, записи аудио, чтения editor text или hard-coded providers в
новом адаптере. Внешняя voice-IME сама отвечает за аудио, распознавание и ввод;
её privacy policy не определяется нашей клавиатурой.

## Проверки и границы доказательств

- `VoiceImeSwitcherTest`: Android metadata + fake system-service boundary;
  non-Google provider, keyboard-only/empty/own package, exact subtype,
  исчезновение провайдера, порядок, отказ/ошибка. Отдельный тест вызывает
  реальный dispatch helper `LatinIME` с fake backend и spy legacy trigger:
  fallback только при UNAVAILABLE, не после REQUESTED/FAILED.
- API 24/37 CI запускает эти тесты вместе с существующими device tests.
  Они **не переключают реальную системную IME** и не записывают звук.
- Общий visibility gate проверяет три точных query в source, APK и AAB;
  mutation tests запрещают исчезновение voice query и преждевременную подмену
  его recognizer query. Permissions и preference keys защищены existing gates.
- Локально: `prek run --all-files`. При наличии JDK 21/SDK — также manual
  `android-unit`, `android-lint`, build и device gates из testing strategy.

До приёмки на устройстве проверить: включённая Google voice-IME; сторонняя
voice-IME, объявляющая enabled `voice` subtype; несколько провайдеров;
отключение/удаление между нажатиями; возврат в HK и обычный набор без потерь;
режимы `voice_mode` main/symbol/off. Не считать поддержку FUTO подтверждённой
по одному названию subtype. Под target >=30 повторить с внешней fixture IME
из UID клавиатуры, включая отрицательный контроль без query.

## S2.09b: следующий PR и условия удаления JAR

1. Заменить intent bridge приватным Activity/result flow на публичном
   `RecognizerIntent`; добавить ровно его query и reviewed component contract.
2. Привязать результат к исходной editor session; отменять при focus change,
   process death и повторном запросе. Не логировать/сохранять распознанный текст.
3. Тестировать cancel, пустой/невалидный/поздний result, удаление провайдера,
   recreation, возврат в редактор, отсутствие сервиса и обычный ввод.
4. Обновлять доступность mic без перезаписи пользовательского `voice_mode`.
5. Удалить JAR/build reference после замены обоих путей; зафиксировать license
   inventory и проверить APK/AAB без `com.google.android.voiceime`.

По решению владельца от 21 сентября 2026 полная S2.09b не блокирует target 36
и Internal testing (#95). При этом фактические voice runtime ошибки требуют
исправления; сама смена target не считается проверкой голосового ввода.

Официальные API:
[enabled IME/subtypes](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager),
[switchInputMethod](https://developer.android.com/reference/android/inputmethodservice/InputMethodService),
[subtype mode](https://developer.android.com/reference/android/view/inputmethod/InputMethodSubtype).
