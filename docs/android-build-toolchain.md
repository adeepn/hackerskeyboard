# Android build toolchain

## Целевая матрица Stage 1

| Компонент | Версия | Состояние |
| --- | --- | --- |
| JDK | 17 | зафиксирован в `.java-version` |
| Gradle | 8.13 | зафиксирован wrapper и SHA-256 |
| Android Gradle Plugin | 8.13.2 | зафиксирован в `gradle.properties` |
| Android NDK | 29.0.14206865 (r29) | зафиксирован в `gradle.properties` |
| CMake | 3.22.1 | зафиксирован в `gradle.properties` |
| min SDK | 24 (Android 7.0) | принято в ADR-0001 |
| compile SDK | 36 | зафиксирован в S1.06 |

AGP 8.13 требует Gradle 8.13 и JDK 17 и поддерживает API 36.1. Patch release
8.13.2 выбран внутри этой совместимой линии.

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

Для неё необходимы Android Platform 36, Build Tools 36.0.0, NDK
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

S1.06 поднимает только `compileSdk` с 26 до 36. Это позволяет компилировать код
против актуального Android API, но само по себе не включает новые изменения
поведения платформы: `targetSdk` временно остаётся равен 26. Его переход на 36
выполняется отдельно на Stage 2 вместе с совместимостными изменениями и тестами.

S1.07 подтверждает сборку Android application и исправляет обнаруженные новым
AGP resource/build errors. Следующие native smoke tests выполняются в S1.09.

## JNI dictionary smoke test

S1.09 исполняет минимальный instrumented test на управляемом Gradle эмуляторе
Pixel 2 / API 36:

```sh
./gradlew :app:pixel2Api36DebugAndroidTest \
  --no-daemon \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Тест открывает встроенный `R.raw.main` через `BinaryDictionary`, проверяет
существующее слово `Android`, отсутствующее контрольное слово `Keyboard` и
закрывает native dictionary. Тем самым исполняются загрузка
`libjni_pckeyboard.so`, регистрация JNI, `openNative`, lookup и `closeNative`.

Команда требует Android Emulator, system image API 36 и аппаратную виртуализацию.
В CI она запускается отдельным job на `ubuntu-latest`; обычная сборка APK
остаётся отдельным быстрым gate.

## Namespace и Android DSL

Module namespace и application ID намеренно совпадают с историческим package:

```text
org.pocketworkstation.pckeyboard
```

`namespace` объявлен в `app/build.gradle`; manifest больше не используется как
его источник. Относительные имена Android components продолжают разрешаться в
тот же package. После S1.06 `compileSdk` равен 36, `targetSdk` временно остаётся
26, а `minSdk` равен 24 согласно ADR-0001.

## Репозитории зависимостей

Buildscript и Android modules используют только поддерживаемые публичные
репозитории в фиксированном порядке:

1. `google()` — Android Gradle Plugin, Android Support/AndroidX и Android test
   artifacts;
2. `mavenCentral()` — JVM и остальные опубликованные зависимости.

`jcenter()` запрещён. Добавление другого repository требует отдельного
обоснования происхождения, доступности и лицензионной совместимости artifacts.
