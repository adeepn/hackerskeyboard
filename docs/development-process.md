# Процесс разработки v2

## Ветки

- `v2` — основная интеграционная и будущая default branch.
- `master` — замороженная историческая база v1.
- Рабочая ветка создаётся только от актуальной `v2`.
- Один небольшой issue обычно соответствует одной ветке и одному PR.
- Все PR направляются в `v2`; PR новой разработки в `master` не принимаются.

Рекомендуемое имя ветки:

```text
issue-<number>/<short-description>
```

До начала работы исполнитель синхронизирует `v2`, проверяет связанные issues и
объявляет файлы/подсистему, которые будет менять. Это особенно важно для
`LatinIME.java`, `LatinKeyboardBaseView.java`, Gradle и manifest: параллельные
задачи в этих hotspots назначаются только при непересекающемся scope.

До реализации bug fix или compatibility change исполнитель также ищет похожие
issues и PR в `klausw/hackerskeyboard`. Найденные ссылки и выводы добавляются в
нашу issue. Upstream patch не считается проверенным только потому, что он
собирается или имеет положительный комментарий: оцениваются diff, тесты,
устройство/API, обсуждения и известные регрессии.

## Иерархия GitHub

Каждому этапу соответствуют:

1. milestone;
2. epic/tracking issue с целью, exit criteria и списком дочерних issues;
3. небольшие issues, пригодные для одного исполнителя или агента;
4. один PR на каждую implementation issue;
5. research issue, завершающийся документацией/ADR, если код не требуется.

Issue готова к агентской разработке (`agent-ready`), если содержит:

- контекст и наблюдаемую проблему;
- точный scope и явно исключённые изменения;
- затрагиваемые подсистемы и зависимости;
- acceptance criteria;
- необходимые автоматические и ручные тесты;
- ожидаемые артефакты;
- известные риски и блокеры.

Для задачи, импортирующей или адаптирующей внешний код, дополнительно обязательны
лицензия источника, repository URL, точный commit hash, список сохранённых
notices и решение о необходимом `NOTICE`/third-party attribution.

## Обязательность тестов

Каждое изменение production code сопровождается тестом соответствующего уровня.
Для bug fix сначала добавляется воспроизводящий regression test, если это
технически возможно. Если автоматизация пока невозможна, issue и PR обязаны
содержать воспроизводимый manual case; создание автоматизации остаётся связанной
задачей этапа 3.

Минимальные проверки PR:

- `prek run --all-files`;
- unit tests затронутого слоя;
- Android lint для Android-изменений;
- debug и release build для build/resource/manifest изменений;
- instrumentation/device tests для IME lifecycle и UI;
- ABI и 16 KB checks для C++/NDK;
- ручная проверка, если она указана в issue.

Набор обязательных команд растёт вместе с этапом 1. Отсутствующий тестовый слой
не маскируется успешной пустой проверкой: PR явно указывает, что ещё невозможно
запустить и какой issue это исправляет.

## Локальные проверки через prek

Проект использует `prek` как единый runner Git hooks. Первичная настройка:

```sh
prek install --prepare-hooks
prek run --all-files
prek run --hook-stage manual android-lint
```

Обычный commit запускает проверки staged files. Перед push/PR исполнитель всегда
запускает полный набор `prek run --all-files`. Запрещено обходить проверки через
`--no-verify` без явного разрешения владельца проекта.

Конфигурация находится в `prek.toml`. Быстрые проверки входят в обычный
pre-commit stage. Android lint доступен отдельным manual hook из-за требований
к JDK/SDK и времени выполнения; Android lint job в CI запускает тот же hook.
Подробности baseline и его triage описаны в `docs/android-lint.md`.

На bootstrap-этапе автоматическое исправление whitespace/line endings намеренно
ограничено новыми файлами `AGENTS.md`, `docs/`, `.github/` и `prek.toml`.
Legacy Java/XML содержит большой formatting debt; массовая правка сделала бы
миграционные PR непроверяемыми. Расширять охват нужно отдельными механическими
issues, не смешивая форматирование с изменением поведения.

## GitHub Actions

- Используются бесплатные GitHub-hosted runners `ubuntu-latest`.
- Workflow запускаются для PR в `v2` и push в `v2`.
- Минимальный workflow запускает `prek run --all-files` через официальный
  `j178/prek-action`.
- После восстановления сборки добавляются отдельные required jobs: build, lint,
  unit tests и native checks.
- Instrumentation/emulator matrix добавляется отдельно с cache и ограничением
  времени.
- Jobs должны иметь минимальные permissions и отменять устаревшие запуски того
  же PR.
- Если лимитов бесплатных runners станет недостаточно, сначала документируются
  длительность, нагрузка и необходимые runner labels; подключение частных
  runners выполняет владелец.

CI не заменяет локальные проверки: локальный `prek` даёт быстрый feedback, CI
подтверждает результат в чистом Linux-окружении.

Release signing не выполняется в PR и push workflow. Ручной workflow доступен
только для `v2`; его signing job использует GitHub Environment `release` и не
делает checkout репозитория. Владелец явно выбрал branch restriction без
required reviewer для Environment. Это не отменяет обычные PR/CI/owner gates и
компенсируется разделением build/sign jobs, проверкой публичного SHA-256
сертификата и отсутствием секретов во всех командах Gradle. Полная модель угроз
и процедура выпуска описаны в `release-signing.md`.

## Обязательные ревью перед merge

Каждый PR проходит три независимых gate:

1. все локальные и CI-тесты успешны;
2. Codex review финального diff;
3. review и approval владельца проекта.

Для Codex review используется финальный diff относительно `v2`. Ревью
должно искать correctness, regression, security/privacy, Android lifecycle,
совместимость и недостаточные тесты. В prompts запрещено передавать секреты,
signing data или реальные пользовательские тексты.

В PR фиксируются:

- commit SHA, который проверялся;
- результат Codex review;
- результат дополнительного model review, если владелец его запросил;
- ссылки на исправления или обоснование отклонённых замечаний;
- локальные команды и CI runs.

После существенного исправления SHA меняется — тесты и Codex review повторяются.
Approval старого diff не переносится автоматически.

Дополнительное review локальной моделью, включая Claude CLI, необязательно и
запускается только владельцем либо по его точному разрешению. Агенты не запускают
Claude CLI, если в окружении задан `ANTHROPIC_API_KEY`, без отдельного явного
разрешения владельца: наличие ключа означает возможный расход внешнего API
budget. Секреты моделей нельзя передавать GitHub-hosted runner без отдельного
решения владельца. Дополнительная модель не заменяет тесты, Codex review или
approval владельца.

## Merge policy

PR нельзя merge, пока хотя бы один gate не выполнен. После назначения `v2`
default branch следует включить branch protection/ruleset:

- pull request required;
- owner approval required;
- required status checks;
- conversations resolved;
- branch up to date before merge;
- force push и deletion запрещены;
- direct push ограничен владельцем для аварийных операций.

Предпочтителен squash merge для небольших issues. Заголовок итогового commit
содержит issue number. При переносе fork code сохраняются repository, commit hash
и attribution.

## Definition of Done для issue

- acceptance criteria выполнены;
- тесты добавлены и проходят локально/в CI;
- документация обновлена;
- PR направлен в `v2` и связан через `Closes #N`;
- Codex рассмотрел финальный SHA;
- владелец одобрил PR;
- все обсуждения разрешены;
- после merge epic checklist и связанные зависимости обновлены.
