# Настройка окружения разработчика

## Требуемая Java

Проект фиксирует JDK 17 в файле `.java-version`. Эта версия используется для
локальных Gradle-команд и в GitHub Actions. Не следует запускать сборку через
случайно выбранную системную Java или более новый Homebrew JDK.

Подходит любой совместимый JDK 17. В CI используется Eclipse Temurin 17.

Для полной Android-сборки дополнительно установите через Android SDK Manager:

- Android SDK Platform 36;
- Android SDK Build Tools 36.0.0;
- Android NDK 29.0.14206865;
- CMake 3.22.1.

Затем выполните:

```sh
./gradlew :app:assembleDebug :app:assembleRelease --no-daemon
scripts/verify-apk-native-libs.sh \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Release APK на этом этапе не подписан и предназначен только для проверки
release-конфигурации, ресурсов и native packaging.

Android lint запускается той же командой `prek`, которую использует отдельный
CI job:

```sh
prek run --hook-stage manual android-lint
```

Hook сделан manual, потому что ему нужны JDK 17 и полный Android SDK, а полный
анализ заметно тяжелее быстрых pre-commit checks. Перед PR с изменениями Android
кода или ресурсов он обязателен. Прямой эквивалент для диагностики:

```sh
./gradlew :app:lintDebug --no-daemon --stacktrace
```

Текущий legacy debt зафиксирован в `app/lint-baseline.xml`; новые warnings и
errors не входят в baseline и останавливают локальную проверку и CI.

Абсолютный путь к JDK зависит от машины и не сохраняется в репозитории.

## Проверка активной Java

Перед Gradle-командами выполните:

```sh
java -version
./gradlew --version --no-daemon
```

Обе команды должны показывать JVM major version 17.

### macOS

Если установленный JDK зарегистрирован в macOS, выберите его для текущей shell:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

Затем повторите проверку версии. Инструменты вроде `jenv`, `asdf` и `mise`
могут автоматически прочитать `.java-version`.

### Linux

Установите JDK 17 средствами используемого дистрибутива или менеджера версий,
затем задайте `JAVA_HOME` на каталог выбранного JDK и добавьте его `bin` в
`PATH`. Не добавляйте машинный абсолютный путь в `gradle.properties` или другие
tracked files.

## Текущее ограничение legacy build

Gradle wrapper 8.13 запускается на JDK 17, но Android Gradle Plugin 3.2.1 ещё не
совместим с этим Gradle API. До выполнения S1.03 команда `./gradlew test` может
остановиться при применении Android plugin с ошибкой, связанной с
`BuildCompletionListener`. Это известный toolchain blocker, а не результат
исполнения тестов приложения.

## Дополнительный Claude CLI review и API-ключи

Наличие Claude CLI не означает, что используется локальная модель. Если в
окружении задан `ANTHROPIC_API_KEY`, CLI может расходовать внешний API budget и
передавать ему контекст.

Codex и другие агенты не запускают Claude CLI при наличии этого ключа без
отдельного явного разрешения владельца. Claude-review не является обязательным.
Если владелец запрашивает и запускает такое дополнительное review, в PR
добавляется только его краткий результат без секретов и исходных значений
переменных окружения.
