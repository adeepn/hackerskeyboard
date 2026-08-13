# Architecture Decision Records

Создавайте ADR для решений, которые трудно безопасно отменить: изменение
`minSdk`, замена JNI, новый формат раскладок, Compose keyboard surface, изменение
application ID или модели совместимости dictionary packs.

Именование: `NNNN-short-title.md`.

Минимальная структура ADR:

1. статус и дата;
2. контекст и ограничения;
3. рассмотренные варианты;
4. решение;
5. последствия и план отката;
6. данные/тесты, на которых основано решение.

## Принятые решения

- [ADR-0001](0001-android-7-minimum-and-native-toolchain.md) — Android 7.0 /
  API 24 как минимальная версия, NDK r29 и CMake 3.22.1.
- [ADR-0002](0002-v2-application-identity.md) — постоянный application ID и
  namespace `io.github.baodeep.hackerskeyboard`, сосуществование с v1 и границы
  миграции настроек/подписи.
