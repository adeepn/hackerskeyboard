# Gradle wrapper

Репозиторий использует Gradle wrapper 9.6.1 как воспроизводимую точку входа в
сборку. Это последний стабильный Gradle, совместимый с Android Gradle Plugin
9.3.1 и Android API 37 на дату обновления.

## Состав

- `gradlew` и `gradlew.bat` — launcher scripts;
- `gradle/wrapper/gradle-wrapper.jar` — официальный wrapper JAR Gradle 9.6.1;
- `gradle/wrapper/gradle-wrapper.properties` — URL и SHA-256 дистрибутива.

Используется компактный официальный дистрибутив
`https://services.gradle.org/distributions/gradle-9.6.1-bin.zip` с SHA-256:

```text
9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
```

Wrapper JAR сгенерирован официальным дистрибутивом Gradle 9.6.1. Его SHA-256:

```text
497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7
```

Оба значения опубликованы в официальном справочнике Gradle release checksums.
Gradle распространяется под Apache License 2.0.

## Проверка

Для запуска wrapper требуется JDK 17:

```sh
./gradlew --version --no-daemon
```

GitHub Actions дополнительно проверяет wrapper JAR через официальный
`gradle/actions/wrapper-validation` и выполняет bootstrap на JDK 17.

Wrapper, AGP и `compileSdk` обновляются как единая совместимая матрица:
Gradle 9.6.1, AGP 9.3.1 и API 37. Откат только одного элемента этой матрицы
запрещён: он снова сделает AndroidX Core 1.19.0 неразрешимой зависимостью.
