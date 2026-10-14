# Gradle wrapper

Репозиторий использует Gradle wrapper 9.8.1 как воспроизводимую точку входа в
сборку вместе с Android Gradle Plugin 9.3.1 и Android API 37.

## Состав

- `gradlew` и `gradlew.bat` — launcher scripts;
- `gradle/wrapper/gradle-wrapper.jar` — официальный wrapper JAR Gradle 9.8.1;
- `gradle/wrapper/gradle-wrapper.properties` — URL и SHA-256 дистрибутива.

Используется компактный официальный дистрибутив
`https://services.gradle.org/distributions/gradle-9.8.1-bin.zip` с SHA-256:

```text
dce76f55f8e251a3a1f130eb120f30b3d271de2b76c9b0729d316b5a1b6dc01f
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
Gradle 9.8.1, AGP 9.3.1 и API 37. Откат только одного элемента этой матрицы
запрещён: он снова сделает AndroidX Core 1.19.1 неразрешимой зависимостью.

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
