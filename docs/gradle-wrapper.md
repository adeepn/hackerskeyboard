# Gradle wrapper

Репозиторий использует Gradle wrapper 8.13 как воспроизводимую точку входа в
сборку. Версия выбрана в соответствии с запланированным переходом на Android
Gradle Plugin 8.13.x и Android API 36.

## Состав

- `gradlew` и `gradlew.bat` — launcher scripts;
- `gradle/wrapper/gradle-wrapper.jar` — официальный wrapper JAR Gradle 8.13;
- `gradle/wrapper/gradle-wrapper.properties` — URL и SHA-256 дистрибутива.

Используется компактный официальный дистрибутив
`https://services.gradle.org/distributions/gradle-8.13-bin.zip` с SHA-256:

```text
20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
```

Wrapper JAR получен из официального тега Gradle `v8.13.0`. Его SHA-256:

```text
81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
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

Этот шаг ещё не обновляет legacy Android Gradle Plugin и build scripts. Их
совместимость с Gradle 8.13 восстанавливается отдельной задачей S1.03.
