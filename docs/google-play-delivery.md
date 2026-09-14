# Доставка alpha-версий через Google Play

По решению владельца от 14 сентября 2026 года S2.22 вынесен вперёд: получение
обновлений через Play нужно во время модернизации, а не только после неё.
Tracking issue: [#79](https://github.com/adeepn/hackerskeyboard/issues/79).
Целевой канал — **Internal testing**. Публичный production rollout остаётся
отдельным решением после compatibility checks.

## Порядок выполнения

| Шаг | Результат | Зависимость |
|---|---|---|
| [S2.22a / #80](https://github.com/adeepn/hackerskeyboard/issues/80) | CI собирает и проверяет unsigned release AAB; артефакт доступен для следующего шага | Текущий toolchain; выполняется сразу после S2.04 |
| [S2.22b / #81](https://github.com/adeepn/hackerskeyboard/issues/81) | Первый подписанный AAB принят Play, владелец включён в internal testers, проверено обновление установленного APK | S2.22a, настройка Console и устранение фактических upload blockers |
| [S2.22c / #82](https://github.com/adeepn/hackerskeyboard/issues/82) | После merge и зелёного CI версия автоматически публикуется в internal | Подтверждённый первый выпуск и Play API access |

Нумерация S2.05–S2.21 сохраняется. Эти compatibility tasks продолжаются после
подготовки доставки; выявленные Play blockers получают приоритет. Не требуется
ждать всех фич этапа 4 или целевого API 37, чтобы начать настройку канала.

## Что уже есть и что ещё не подтверждено

Есть owner-signed APK, стабильный package `com.baodeep.hackerskeyboard`,
GitHub Environment `release` и CI на `ubuntu-latest`. Последний APK от
21 августа: commit `d9f33ce`, версия `2.0.0-alpha03`, код `2000003`.
Две разные сборки использовали этот код; для Play так делать нельзя.
Первый новый выпуск должен иметь код выше `2000003` и всех ранее загруженных
в Console кодов. Повторная сборка не должна создавать коллизию версий.

Владелец сообщил о регистрации package и сертификата. Это не доказательство
завершённой настройки **Play App Signing**, API access или первого выпуска.
На 14 сентября в проверенных repository/release GitHub settings нет настроек
Play API. Первая загрузка и автоматическая доставка ещё не проверены.

Текущий `targetSdk` — 26. Общая политика Google с 31 августа 2026 требует API
36+ для новых приложений и обновлений. В проверенных официальных документах не
найдено явного исключения для обычного Internal testing. Возможность загрузки
нашего bundle проверяется в Console, а её результат сохраняется в #81.
Не поднимать target ради загрузки без runtime tests; если Play отклонит bundle,
нужные compatibility tasks становятся условием запуска канала.
[Требования target API](https://support.google.com/googleplay/android-developer/answer/11926878).

## AAB в CI и локально

В S2.22a обычный `Quality gates` job создаёт unsigned AAB после APK checks,
проверяет его и сохраняет отдельный artifact
`hackers-keyboard-unsigned-aab-<sha>` на 14 дней. PR artifact относится к
проверяемому GitHub merge SHA. Для выпуска используется только проверенный
commit `v2`. Unsigned AAB нельзя установить на телефон или считать готовым
публикационным артефактом.

При установленном Android SDK и проектном JDK 21:

```sh
prek run --all-files
prek run --all-files --hook-stage manual release-bundle
```

Вторая команда вызывает `bash scripts/build-release-bundle.sh`: Gradle
`:app:bundleRelease`, затем `scripts/verify-release-bundle.py`. Проверяются:

- структура bundle официальным `bundletool validate`;
- package, versionCode/versionName из `gradle.properties`, min/target из Gradle;
- release `debuggable=false` и наличие application;
- четыре native ABI с ELF `libjni_pckeyboard.so`;
- целостность ZIP и SHA-256 артефакта.

`bundletool` 1.18.3 загружается из официального `google/bundletool` release и
проверяется по опубликованному asset digest
`a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`
перед запуском. Кеш: `.gradle/bundletool/`. Можно указать заранее загруженный
JAR через `--bundletool`; SHA-256 проверяется и для него. Offline verifier tests
входят в обычный `prek`, загрузка JAR и Android build — только в manual gate/CI.
Это проверка сборки, не подтверждение Play acceptance, 16 KB совместимости или
поведения APK splits. Эти проверки входят в последующие шаги.

## Однократная настройка владельцем

В #81 фиксируем результаты следующих действий в Play Console:

1. Проверить app record и настройку Play App Signing. Device-side app signing
   certificate должен совпасть с текущим owner APK:
   `379073484D33DCDB34A2E67766A6A2836790071991DD2B65ADDF315447B2B50D`.
   Отдельный upload key допустим; он не заменяет ключ, которым Play подписывает
   APK для телефона. Смена device-side key не входит в этот план.
2. Загрузить первый signed AAB и завершить требуемые Console шаги. API Edits
   работает с уже заведённым приложением; первичная загрузка выполняется через
   Console. Возможный review Google не считается мгновенным или обходным.
3. Создать список internal testers с аккаунтом владельца, открыть opt-in link и
   включить автообновления в Play Store. Проверить update поверх установленной
   alpha03, сохранность настроек и состояния IME.
4. В #82 включить Google Play Developer API в Google Cloud и предоставить
   сервисному аккаунту права только на наше приложение и тестовые релизы.
   Предпочтительный доступ GitHub — OIDC / Workload Identity Federation; точные
   настройки и permission names будут проверены в implementation PR. Приватные
   credentials не передаются в чат; настройка производится в Cloud/GitHub.

AAB подписывается `jarsigner`, а не `apksigner`. Подпись остаётся отдельной
стадией после сборки: signing/API credentials не попадают в Gradle или PR jobs.
S2.22b расширяет `Signed v2 release`: один запуск из `v2` создаёт APK и AAB
с одинаковой версией; первый кандидат — alpha04 / `2000004`. Артефакт
`hackers-keyboard-v2-<version>-<commit>` содержит оба файла и evidence подписи.
Bundle проходит проверку до подписи, strict signature verification и сравнение
payload после неё. Запуск workflow не публикует ничего в Play автоматически.
Для первой загрузки нужно распаковать artifact ZIP и выбрать `.aab`, не APK и
не ZIP. После загрузки сохранить фактические Console errors/acceptance в #81.
[Bundletool и подпись AAB](https://developer.android.com/tools/bundletool),
[начальная настройка API](https://developers.google.com/android-publisher/getting_started),
[границы Edits API](https://developers.google.com/android-publisher/edits).

## Автоматическая доставка после bootstrap

Целевая последовательность: approved PR → merge в `v2` → зелёные CI/security
checks того же commit → release AAB → отдельная подпись → публикация `internal`
→ обновление через Play Store. Повторное ручное создание release не требуется.
Расписание установки зависит от Play и настроек телефона.

Workflow должен проверять origin repository, событие `push`, ветку `v2` и SHA;
события от PR/fork не дают доступа к публикации. Deploy jobs сериализуются,
устаревшие запуски пропускаются. Нумерация строго возрастает, повторный запуск
обрабатывается без повторного использования кода для другого bundle. Evidence
связывает commit, CI run, versionCode, SHA-256 AAB и результат Play API.
Upload без опубликованного track release не считается доставкой пользователю.
Production track автоматически не обновляется.

Готовность #79 подтверждается двумя последовательными обновлениями через Play
после однократного opt-in владельца. До этого не отмечаем доставку выполненной.
[Internal testing и обновления](https://support.google.com/googleplay/android-developer/answer/9845334).
