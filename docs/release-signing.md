# Подпись и выпуск Hacker's Keyboard v2

## Цель и постоянная identity

Публикуемый пакет — `com.baodeep.hackerskeyboard`. Первая alpha имеет
`versionCode 2000001` и `versionName 2.0.0-alpha01`. Любой следующий APK с тем
же application ID обязан быть подписан тем же owner-controlled ключом и иметь
строго больший `versionCode`; иначе Android не примет его как обновление.

Старый Hacker's Keyboard `org.pocketworkstation.pckeyboard` остаётся отдельным
приложением и может быть установлен одновременно. Ранее скачанные из CI debug
APK нового пакета подписывались эфемерными debug keys. Перед первой установкой
стабильно подписанного APK такой debug APK придётся один раз удалить. После
этого новые owner-signed версии должны устанавливаться поверх предыдущих.

## Где находятся ключ и публичная проверка

Приватный ключ не хранится в Git. В GitHub Environment `release` настроены:

| Имя | Kind | Содержимое |
|---|---|---|
| `V2_RELEASE_KEYSTORE_BASE64` | secret | Base64 одного PKCS12-контейнера с release key |
| `V2_RELEASE_KEY_ALIAS` | secret | alias ключа в контейнере |
| `V2_RELEASE_STORE_PASSWORD` | secret | пароль PKCS12; применяется и как key password |
| `V2_RELEASE_STORE_TYPE` | variable | `PKCS12` |
| `V2_RELEASE_CERT_SHA256` | variable | публичный SHA-256 сертификата |

Отдельный `KEY_PASSWORD` не требуется для текущего PKCS12. Если формат ключа
изменится, сначала меняются документация и workflow отдельным PR. Сами secret
values, приватный ключ и декодированный keystore нельзя печатать в logs,
прикладывать к issue/PR либо передавать агентам и моделям.

Environment разрешает только exact branch `v2`. По явному решению владельца у
него нет required reviewer. Поэтому security boundary строится не на pause,
а на минимальном scope и изоляции jobs:

1. `build-release` checkout’ит `v2`, запускает Gradle без secrets, проверяет
   identity, ABI и size budget, затем отдаёт unsigned APK;
2. `sign-release` получает Environment secrets, но не checkout’ит Git и не
   запускает Gradle или repository code;
3. keystore декодируется только в `$RUNNER_TEMP`, имеет mode `0600` и удаляется
   через `trap`;
4. `apksigner` подписывает подготовленный APK, сверяет публичный certificate
   SHA-256, package, обе версии и `debuggable=false`;
5. наружу выгружаются только signed APK и публичный `release-evidence.txt`.

Обычные `pull_request` и `push` jobs никогда не получают signing secrets.
Workflow и third-party actions для signing path закреплены полными commit SHA.

## Оптимизация и функциональная безопасность

Release variant использует R8 (`minifyEnabled`) и resource shrinking. Это
компенсирует рост после AndroidX, не возвращая устаревшую Support Library.
Максимальный размер unsigned alpha APK — 3 400 000 байт; gate действует и в
обычном CI, и перед подписью.

Два legacy-механизма требуют явных keep contracts:

- native `JNI_OnLoad` ищет
  `com.baodeep.hackerskeyboard.BinaryDictionary` и методы по точным именам,
  поэтому класс сохраняется ProGuard/R8 rule;
- встроенный `@raw/main` выбирается через `Resources.getIdentifier`, поэтому
  resource keep XML запрещает shrinker удалить словарь.

Debug instrumentation smoke на API 24 и API 37 проверяет application/JNI.
Minified release дополнительно обязан собраться, пройти identity/ABI/size gates
и быть установлен вручную до признания alpha пригодной.

## Выпуск

1. Merge release change в `v2` только после зелёного CI, Codex review и approval
   владельца.
2. В GitHub Actions открыть `Signed v2 release`, выбрать `Run workflow`, branch
   `v2`.
3. Убедиться, что оба jobs зелёные. Скачать artifact
   `hackers-keyboard-v2-<version>-<commit>`.
4. Сверить `release-evidence.txt` с commit в `v2`; при внешней публикации
   опубликовать SHA-256 APK и сертификата рядом с файлом.
5. Проверить свежую установку на поддерживаемой версии Android. Затем собрать
   тестовый APK с большим `versionCode` тем же workflow и проверить update без
   удаления данных/IME settings.

Не следует публиковать обычный debug APK как v2 release: его сертификат не
стабилен между GitHub runners. Нельзя вручную переподписывать опубликованный
APK другим ключом или повторно использовать уменьшенный `versionCode`.

## Backup и восстановление

Владелец хранит оригинал ключевого материала и пароль вне GitHub как минимум в
двух независимых защищённых местах. GitHub secret является deployment copy, а
не единственным backup. Потеря private key делает дальнейшие обновления
существующей установки невозможными; смена сертификата требует отдельного
исследования Android signing key rotation и release-channel policy.
