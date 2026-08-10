# Архитектура и исходный технический аудит

## Масштаб проекта

На исходной точке `v2` проект содержит приблизительно:

- 17 639 строк Java;
- 1 999 строк C++/JNI;
- 519 Android resources;
- один Android application module;
- старый закрытый JAR голосового ввода `app/libs/voiceimeutils.jar`.

Код происходит от AOSP LatinIME эпохи Android 2.3. Последние существенные
изменения основной ветки относятся к 2019–2020 годам.

## Подсистемы

| Подсистема | Основные файлы | Назначение и риск |
|---|---|---|
| IME lifecycle | `LatinIME.java` | Центральный класс в 3 500+ строк; владеет слишком многими состояниями |
| Клавиатура и рендеринг | `Keyboard.java`, `LatinKeyboard.java`, `LatinKeyboardBaseView.java` | XML parsing, Canvas, touch, popup и preview; максимальный риск визуальных и gesture-регрессий |
| Переключение режимов | `KeyboardSwitcher.java`, `LanguageSwitcher.java` | Язык, ориентация, symbol/Fn/full/compact modes |
| Ввод | `EditingUtil.java`, `TextEntryState.java`, `WordComposer.java` | `InputConnection`, key events, composing text, терминальные обходы |
| Подсказки | `Suggest.java`, `CandidateView.java`, expandable dictionaries | Чувствительны к lifecycle и современному отображению candidates view |
| Словари | `BinaryDictionary.java`, `PluginManager.java`, `app/src/main/cpp` | JNI, package visibility, внешние dictionary packs |
| Настройки | классы `*Preference*`, `LatinIMESettings.java` | Основаны на устаревшем `android.preference` |
| Ресурсы | `res/xml-*`, `res/values-*`, `res/layout` | Большая функциональная база раскладок; массовые преобразования опасны |

## Исходная сборка

- AGP 3.2.1;
- `compileSdk 26`, `targetSdk 26`, `minSdk 14`;
- Support Library 26/27;
- `jcenter()`;
- неполный Gradle wrapper: нет `gradle-wrapper.jar` и properties;
- CMake 3.4.1 без зафиксированной версии NDK;
- релизный lint отключён;
- тестовых исходников практически нет.

В локальном окружении на момент аудита доступен JDK 26, но не настроены Android
SDK и системный Gradle. Поэтому исходная сборка не была воспроизведена. Этап 1
должен создать hermetic-ish toolchain на JDK 17, а не зависеть от локального
JDK 26.

## Главные platform gaps

1. Android 12+: отсутствуют `android:exported` и mutability flags у
   `PendingIntent`.
2. Android 11+: обнаружение dictionary packs требует узких `<queries>`.
3. Android 13+: опциональное постоянное уведомление требует корректного flow
   `POST_NOTIFICATIONS`.
4. Android 14+: runtime receivers требуют явной экспортируемости; внутренние
   implicit intents нужно сделать explicit и защищёнными.
5. Android 15/16: JNI необходимо проверить на 16 KB page size, а popup/candidate
   UI — на новых window/insets правилах.
6. `android.preference`, `AsyncTask`, старые locale/resource и vibration API
   требуют замены либо совместимого адаптера.
7. `voiceimeutils.jar` 2011 года не должен оставаться непрозрачной долгосрочной
   зависимостью.
8. Глобальное изменение `Resources.Configuration.locale` создаёт риск утечки
   locale state между раскладками и поломки popup resources.

## Целевая архитектурная граница

На этапах 1–3 текущий движок сохраняется. В этапе 4 `LatinIME` постепенно
разделяется на контроллеры:

- IME lifecycle/session;
- editor/input dispatcher;
- modifier state machine;
- keyboard/layout repository;
- suggestion and dictionary coordinator;
- feedback (sound, vibration, preview);
- notification and system integration;
- settings repository.

Каждая граница сначала вводится интерфейсом и characterization tests, и только
потом переносом реализации. Это позволяет смешивать Java и Kotlin без большого
одновременного rewrite.

## Технологические решения

- Kotlin: для нового изолированного кода и постепенной миграции после тестов.
- Custom View/Canvas: оставить для keyboard surface; это соответствует
  низкоуровневому touch/rendering профилю проекта.
- Compose: допустим как отдельное исследование для settings UI, но не как
  условие v2.
- C++/JNI: оставить до получения benchmark и 16 KB результатов.
- Flutter/React Native/MAUI: не использовать для IME service и keyboard surface.

## Официальные ориентиры

- [Создание Android IME](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [Требования Google Play к target API](https://developer.android.com/google/play/requirements/target-sdk)
- [Поддержка 16 KB page size](https://developer.android.com/guide/practices/page-sizes)
- [Android 12 target behavior](https://developer.android.com/about/versions/12/behavior-changes-12)
- [Android 14 target behavior](https://developer.android.com/about/versions/14/behavior-changes-14)
