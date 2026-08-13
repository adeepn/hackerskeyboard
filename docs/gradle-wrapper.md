# Gradle wrapper

Репозиторий использует Gradle wrapper 9.3.1 как воспроизводимую точку входа в
сборку. Версия является минимальной поддерживаемой для Android Gradle Plugin
9.1.1 и Android API 37.

## Состав

- `gradlew` и `gradlew.bat` — launcher scripts;
- `gradle/wrapper/gradle-wrapper.jar` — официальный wrapper JAR Gradle 9.3.1;
- `gradle/wrapper/gradle-wrapper.properties` — URL и SHA-256 дистрибутива.

Используется компактный официальный дистрибутив
`https://services.gradle.org/distributions/gradle-9.3.1-bin.zip` с SHA-256:

```text
b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
```

Wrapper JAR сгенерирован официальным дистрибутивом Gradle 9.3.1. Его SHA-256:

```text
b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13
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
Gradle 9.3.1, AGP 9.1.1 и API 37. Откат только одного элемента этой матрицы
запрещён: он снова сделает AndroidX Core 1.19.0 неразрешимой зависимостью.
