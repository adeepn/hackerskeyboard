# Gradle wrapper

Репозиторий использует Gradle 9.9.2 через wrapper как воспроизводимую точку входа в
сборку вместе с Android Gradle Plugin 9.3.1 и Android API 37.

## Состав

- `gradlew` и `gradlew.bat` — launcher scripts;
- `gradle/wrapper/gradle-wrapper.jar` — официальный wrapper JAR Gradle 9.8.1;
- `gradle/wrapper/gradle-wrapper.properties` — URL и SHA-256 дистрибутива.

Используется компактный официальный дистрибутив
`https://services.gradle.org/distributions/gradle-9.9.2-bin.zip` с SHA-256:

```text
971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b
```

Wrapper JAR соответствует официальному Gradle 9.8.1. Его SHA-256:

```text
3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98
```

Оба значения опубликованы в [официальном справочнике Gradle release checksums](https://gradle.org/release-checksums/).
Wrapper сгенерирован задачей `wrapper` из проверенного официального ZIP Gradle
9.8.1; source commit: `39daf39feb60a18b1b6ddccde53366522fef516f`.
Gradle распространяется под Apache License 2.0.

## Проверка

Для запуска wrapper требуется проектный JDK 21:

```sh
./gradlew --version --no-daemon
```

GitHub Actions дополнительно проверяет wrapper JAR через официальный
`gradle/actions/wrapper-validation` и выполняет bootstrap на JDK 21.

Wrapper, AGP и `compileSdk` обновляются как единая совместимая матрица:
Gradle 9.9.2, AGP 9.3.1 и API 37. AGP 9.3.1 допускает Gradle 9.5.0 или новее,
поэтому откат Gradle до 9.7.1 сам по себе не делает Core 1.19.1 неразрешимым.
Он возвращает version lint finding, который блокирует текущие release gates.

## Обновление после target 36 bootstrap

[DECISION] 14 октября 2026 владелец явно запросил Gradle 9.8.1 и AndroidX Core
1.19.1 для устранения двух новых lint findings на merge commit PR #96.
GitHub release Gradle опубликован 7 октября в 21:46 UTC: полный семидневный
буфер при подготовке PR ещё не истёк; точная версия выбрана прямым поручением
владельца. CVE identifiers у security advisories не назначены, поэтому это
не заявляется как исключение по именованному CVE.
[Release notes](https://docs.gradle.org/9.8.1/release-notes.html) перечисляют
GHSA-mvvg-497x-hmj8, GHSA-xwqc-3h47-hg64 и GHSA-j5m7-59rp-24f5.
Lint severity, baseline, AGP, JDK, SDK и версия alpha05 не изменяются.

## Patch update 9.8.2

PR #101 обновляет дистрибутив до Gradle 9.8.2. Релиз опубликован 19 октября
2026 года; семидневный буфер истёк. Wrapper JAR и launcher scripts сохраняют
происхождение от 9.8.1, описанное выше. AGP, JDK, SDK и AndroidX не меняются.

## Обновление до 9.9.2

[DECISION] Повторный CI PR #101 от 27 ноября 2026 отклонил 9.8.2:
`AndroidGradlePluginVersion` требует 9.9.2. Обновлён дистрибутив и его
официальный SHA-256; lint и baseline не ослабляются. Релиз опубликован
19 ноября 2026 в 20:59 UTC, семидневный буфер истёк.
[Compatibility matrix](https://docs.gradle.org/9.9.2/userguide/compatibility.html)
включает проектные JDK 21 и AGP 9.3.1. Wrapper JAR/scripts сохраняют provenance
9.8.1; остальные версии toolchain не меняются.

## Политика новых релизов

[DECISION] Wrapper freshness следует reviewed Dependabot PR после семидневного
`cooldown`, а не изменяемой в день релиза подсказке lint. Точная область
исключения и неизменённые compatibility/security gates описаны в
[Android lint](android-lint.md#возраст-релизов-и-wrapper-freshness).
Исторические обновления выше описывают причины на тот момент; появление нового
релиза больше не требует немедленного изменения закреплённого Gradle.
