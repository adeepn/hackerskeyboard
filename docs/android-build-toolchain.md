# Android build toolchain

## Целевая матрица Stage 1

| Компонент | Версия | Состояние |
| --- | --- | --- |
| JDK | 21 | зафиксирован в `.java-version` |
| Gradle | 9.6.1 | зафиксирован wrapper и SHA-256 |
| Android Gradle Plugin | 9.3.1 | зафиксирован в `gradle.properties` |
| Android NDK | 29.0.14206865 (r29) | зафиксирован в `gradle.properties` |
| CMake | 3.22.1 | зафиксирован в `gradle.properties` |
| min SDK | 24 (Android 7.0) | принято в ADR-0001 |
| compile SDK | 37 | поднят в S2.02 для AndroidX Core 1.19.0 |

AGP 9.3.1 требует Gradle 9.5.0 или новее, минимум JDK 17 и поддерживает API 37.
Проект пока фиксирует проверенный Gradle 9.6.1. Стабильный
[Gradle 9.7.1](https://docs.gradle.org/9.7.1/release-notes.html) выпущен
19 августа 2026 года; его обновление выполняется отдельным toolchain PR после
проверки wrapper checksum и полной AGP/SDK/NDK matrix. До этого lint-подсказка
остаётся в существующей точечной baseline-категории. Проект фиксирует JDK 21
LTS: lint из AGP 9.3.1 использует
`List.removeLast()` в `BidirectionalTextDetector`, из-за чего анализ падает на
JDK 17 до создания отчёта. JDK 21 позволяет оставить security detector
`BidiSpoofing` включённым. Эта матрица
выбрана вместо отката AndroidX Core 1.19.0, который требует `compileSdk 37` и
AGP 9.1.0 или новее. Preview-платформа Android 17 публикуется для `sdkmanager`
под точным package ID `platforms;android-37.0`; Build Tools — `37.0.0`.

## Проверка разрешения AGP

Пока legacy Android module не перенесён на новый DSL, применение plugin
последовательно обнаружит следующие migration blockers. Чтобы отдельно
проверять доступность выбранного AGP из Google Maven, используется минимальный
build:

```sh
./gradlew -p verification/agp-resolution resolveAgp --no-daemon
```

Он читает `agpVersion` из корневого `gradle.properties`, разрешает artifact
`com.android.tools.build:gradle` и проверяет полученную версию. Этот build не
компилирует приложение и не считается подтверждением успешной Android-сборки.

## Полная сборка приложения

Debug и unsigned release варианты собираются одной командой:

```sh
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon
```

Для неё необходимы Android Platform 37.0, Build Tools 37.0.0, NDK
29.0.14206865 и CMake 3.22.1. CI устанавливает именно эти версии, не полагаясь
на изменяемый состав образа `ubuntu-latest`.

После сборки состав native libraries проверяется для обоих APK:

```sh
scripts/verify-apk-native-libs.sh \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Успешный `assembleRelease` создаёт неподписанный APK и подтверждает только
release-компиляцию. Он ещё не является публикуемым артефактом: signing,
финальные release checks и AAB относятся к последующим задачам Stage 1/2.

## Разделение compile SDK и target SDK

S1.06 поднял `compileSdk` с 26 до 36, а S2.02 — с 36 до 37 как обязательную
часть перехода на AndroidX Core 1.19.0. Это позволяет компилировать код
против актуального Android API, но само по себе не включает новые изменения
поведения платформы: `targetSdk` временно остаётся равен 26. Его переход на 37
выполняется отдельно на Stage 2 вместе с совместимостными изменениями и тестами.

На современных устройствах промежуточный APK поэтому может показывать системное
предупреждение о приложении, разработанном для старой версии Android. Это
ожидаемое следствие `targetSdk 26`, а не ошибка подписи или повреждение APK.
Предупреждение устраняется последовательной миграцией target API на Stage 2;
поднимать target только ради скрытия диалога без platform-compatibility fixes
нельзя.

S1.07 подтверждает сборку Android application и исправляет обнаруженные новым
AGP resource/build errors. Следующие native smoke tests выполняются в S1.09.

## Application и JNI dictionary smoke tests

S1.09 добавил минимальный JNI instrumented test, а S1.15 расширяет device gate
до проверки установки, регистрации IME и запуска основных activities на нижней
и верхней границах. API 24 запускается через явно создаваемый Android Emulator
и connected test, что сохраняет контролируемый AOSP `x86` образ нижней границы.
Верхняя граница проверяется Gradle Managed Device на API 37. Используются
команды:

```sh
scripts/run-api24-connected-tests.sh
./gradlew :app:pixel2Api37DebugAndroidTest \
  --no-daemon \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

`ApplicationSmokeTest` подтверждает, что test target APK установлен, `LatinIME`
обнаруживается через системный `InputMethodManager`, а setup и settings UI
запускаются и создают обязательные views. `BinaryDictionarySmokeTest` открывает
встроенный `R.raw.main` через `BinaryDictionary`, проверяет существующее слово
`Android`, отсутствующее контрольное слово `Keyboard` и закрывает native
dictionary. Тем самым исполняются загрузка
`libjni_pckeyboard.so`, регистрация JNI, `openNative`, lookup и `closeNative`.

Команды требуют Android Emulator, system images API 24/37 и аппаратную
виртуализацию. API 24 script создаёт чистый AOSP `x86` AVD, ждёт завершения boot
и запускает `connectedDebugAndroidTest`. В CI обе границы выполняются отдельной
matrix job на `ubuntu-latest`; обычная сборка APK остаётся отдельным быстрым gate
и публикует installable debug APK вместе с unsigned release APK. API 37 smoke
явно фиксирует Google 64-bit `x86_64` image и 4 KB page alignment, поэтому не зависит от
меняющихся defaults AGP. Отдельный 16 KB device gate остаётся частью
[#33](https://github.com/adeepn/hackerskeyboard/issues/33) и S2.21.

Этот baseline не утверждает, что уже проверена полная функциональность набора:
нажатия экранных клавиш, modifiers, popup, candidates и interoperability с
редакторами относятся к Stage 3.

## Namespace и Android DSL

После S1.25 module namespace и application ID используют отдельную постоянную
identity v2:

```text
com.baodeep.hackerskeyboard
```

`namespace` объявлен в `app/build.gradle`; manifest больше не используется как
его источник. Код, XML custom views и JNI registration перенесены в тот же
package. Историческое приложение `org.pocketworkstation.pckeyboard` остаётся
отдельным и может быть установлено одновременно. После S1.06 `compileSdk` равен
37, `targetSdk` временно остаётся 26, а `minSdk` равен 24 согласно ADR-0001.

Подробный migration contract, включая отсутствие автоматического переноса
private settings и требования к будущей стабильной release-подписи, зафиксирован
в [ADR-0002](adr/0002-v2-application-identity.md).

## Репозитории зависимостей

Buildscript и Android modules используют только поддерживаемые публичные
репозитории в фиксированном порядке:

1. `google()` — Android Gradle Plugin, Android Support/AndroidX и Android test
   artifacts;
2. `mavenCentral()` — JVM и остальные опубликованные зависимости.

`jcenter()` запрещён. Добавление другого repository требует отдельного
обоснования происхождения, доступности и лицензионной совместимости artifacts.
