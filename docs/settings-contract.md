# Контракт хранения настроек

S2.03a фиксирует состояние настроек до замены platform `android.preference` на
AndroidX Preference. Этот документ и проверяемый снимок
[`settings-contract.json`](settings-contract.json) описывают данные, которые
нельзя незаметно переименовать, сменить по типу или сбросить при миграции UI.

## Зафиксированная поверхность

- шесть XML resources: главный экран, actions, feedback, debug, view и пустой
  контейнер динамического выбора языков;
- 66 XML nodes с `android:key`, включая 18 неперсистентных screen/category/info
  nodes;
- 48 уникальных персистентных XML keys: 18 boolean и 30 string;
- два string keys вне XML: `selected_languages` и `input_language`;
- стандартное хранилище
  `com.baodeep.hackerskeyboard_preferences`, определяемое постоянным application
  ID;
- четыре explicit actions, открывающие вложенные settings screens;
- default values для всех resource qualifiers и значения всех массивов,
  записываемые `ListPreference`.

В текущем контракте нет `float` или `string-set` значений. Даже визуальные
ползунки используют `SeekBarPreferenceString`: сохранение числа как `float`
сломало бы чтение уже существующего string value через `SharedPreferences`.

Снимок намеренно хранит semantic type отдельно от полного Android class name.
Поэтому стандартный `CheckBoxPreference` может перейти из platform package в
AndroidX, но смена boolean на string всё равно остановит проверку. Custom widget
names остаются частью снимка до их отдельной миграции. Extractor одинаково
понимает legacy `android:` и AndroidX `app:` preference attributes, поэтому
техническая смена namespace сама по себе не считается изменением данных.

## Автоматическая проверка

Локально и в CI выполняются:

```sh
python3 scripts/test-settings-contract.py
python3 scripts/verify-settings-contract.py
```

Первая команда проверяет extractor/diff и доказывает, что удаление key или смена
типа обнаруживаются. Вторая заново выводит фактический контракт из XML, Java и
resource variants и сравнивает его с committed JSON fixture. Обе команды входят
в `prek run --all-files`.

## Осознанное изменение контракта

Изменять fixture только ради получения зелёного теста запрещено. Сначала нужно:

1. завести отдельную issue с пользовательской причиной и migration strategy;
2. добавить тест чтения старого значения и записи нового состояния;
3. проверить update поверх подписанного предыдущего APK;
4. получить новый снимок командой
   `python3 scripts/verify-settings-contract.py --print-contract`;
5. проверить semantic diff и только затем обновить fixture через отдельный PR.

S2.03 не меняет keys, storage types или defaults. Если такое изменение окажется
необходимым, оно выносится за границы AndroidX UI migration.
