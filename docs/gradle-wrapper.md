# Gradle wrapper

Репозиторий использует Gradle wrapper 9.7.1 как воспроизводимую точку входа в
сборку вместе с Android Gradle Plugin 9.3.1 и Android API 37.

## Состав

- `gradlew` и `gradlew.bat` — launcher scripts;
- `gradle/wrapper/gradle-wrapper.jar` — официальный wrapper JAR Gradle 9.7.1;
- `gradle/wrapper/gradle-wrapper.properties` — URL и SHA-256 дистрибутива.

Используется компактный официальный дистрибутив
`https://services.gradle.org/distributions/gradle-9.7.1-bin.zip` с SHA-256:

```text
acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
```

Wrapper JAR соответствует официальному Gradle 9.7.1. Его SHA-256:

```text
7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d
```

Оба значения опубликованы в официальном справочнике Gradle release checksums.
Gradle распространяется под Apache License 2.0.

## Проверка

Для запуска wrapper требуется проектный JDK 21:

```sh
./gradlew --version --no-daemon
```

GitHub Actions дополнительно проверяет wrapper JAR через официальный
`gradle/actions/wrapper-validation` и выполняет bootstrap на JDK 21.

Wrapper, AGP и `compileSdk` обновляются как единая совместимая матрица:
Gradle 9.7.1, AGP 9.3.1 и API 37. Откат только одного элемента этой матрицы
запрещён: он снова сделает AndroidX Core 1.19.0 неразрешимой зависимостью.
