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
| compile SDK | 36 | запланирован в S1.06 |

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

## Следующие blockers

После S1.05 остаются отдельные изменения:

1. S1.06 — поднять `compileSdk` до 36;
2. S1.07 — исправить обнаруженные новым AGP resource/build errors.

## Namespace и Android DSL

Module namespace и application ID намеренно совпадают с историческим package:

```text
org.pocketworkstation.pckeyboard
```

`namespace` объявлен в `app/build.gradle`; manifest больше не используется как
его источник. Относительные имена Android components продолжают разрешаться в
тот же package. После ADR-0001 compile/target SDK временно остаются 26, а min
SDK равен 24.

## Репозитории зависимостей

Buildscript и Android modules используют только поддерживаемые публичные
репозитории в фиксированном порядке:

1. `google()` — Android Gradle Plugin, Android Support/AndroidX и Android test
   artifacts;
2. `mavenCentral()` — JVM и остальные опубликованные зависимости.

`jcenter()` запрещён. Добавление другого repository требует отдельного
обоснования происхождения, доступности и лицензионной совместимости artifacts.
