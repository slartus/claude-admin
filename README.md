# Claude Admin

Десктопное приложение (Kotlin + Compose for Desktop) для удобного администрирования
проектов, использующих [Claude Code](https://docs.claude.com/en/docs/claude-code).
Объединяет в одном окне список проектов, просмотр их `CLAUDE.md` / агентов / команд /
`settings.local.json`, встроенные PTY-терминалы и индикатор текущей git-ветки.

## Возможности

- Список проектов с иконками-бейджами и сохранением в `~/.claude-admin/projects.json`.
- Боковая панель: проекты + открытые терминалы (по нескольку на проект).
- Встроенный PTY-терминал на базе [pty4j](https://github.com/JetBrains/pty4j) +
  [JediTerm](https://github.com/JetBrains/jediterm).
- Подсказка текущей git-ветки рядом с проектом, реактивно обновляется при
  `git checkout` (через `WatchService` на `.git/HEAD`).
  - Авто-поиск `.git` вверх по иерархии (для подпапок монорепы).
  - Если репозиторий не найден — диалог с предложением выбрать root вручную;
    путь сохраняется в `Project.gitRoot`.
- Просмотр `CLAUDE.md`, локальных `settings.local.json`, списков агентов и slash-команд
  проекта.
- Подтверждение закрытия активных терминалов при выходе.

## Архитектура

Многомодульный Gradle-проект, разделённый по слоям:

| Модуль          | Назначение                                                                |
| --------------- | ------------------------------------------------------------------------- |
| `:domain`       | Чистые модели (`Project`, `GitStatus`, `TerminalSession`, …), репозитории-интерфейсы, use-cases. |
| `:data`         | File-based реализации репозиториев, парсер `.git/HEAD`, обёртка pty4j.    |
| `:presentation` | `RootComponent` (Decompose) + `RootState` — единый UDF-стейт UI.          |
| `:app`          | Compose for Desktop UI, DI (Koin), точка входа, упаковка под macOS.       |

Поток данных однонаправленный: `Repository → UseCase → RootComponent (StateFlow) → Composable`.
Все мутации стейта проходят через методы `RootComponent`.

## Стек

- Kotlin 2.x, JVM toolchain 17
- Compose for Desktop (Material 3)
- Decompose — навигация / scope корневого компонента
- Koin — DI
- kotlinx.coroutines + Flow / SharedFlow
- kotlinx.serialization (JSON), kaml (YAML)
- pty4j + JediTerm — терминал
- JUnit 5 для тестов

## Сборка и запуск

Требуется JDK 17+.

```sh
# запуск из исходников
./gradlew :app:run

# проверка компиляции всех модулей
./gradlew compileKotlin

# тесты
./gradlew test

# собрать macOS .dmg
./gradlew :app:packageDmg
```

Опционально для упаковки можно указать собственный JDK с `jpackage`:

```properties
# local.properties
compose.javaHome=/Library/Java/JavaVirtualMachines/<jdk>/Contents/Home
```

## Где хранятся данные

- `~/.claude-admin/projects.json` — список проектов и опциональный `gitRoot`.
- `~/.claude` — пользовательский каталог Claude Code (читается, не модифицируется).
- Внутри каждого проекта читаются: `CLAUDE.md`, `.claude/settings.local.json`,
  `.claude/agents/*`, `.claude/commands/*`.

## Структура каталогов

```
domain/         модели, репозитории-интерфейсы, use-cases
data/           File*Repository, FileGitRepository (WatchService), pty4j-обёртки
presentation/   RootComponent + RootState
app/            Compose UI, DI, Main
```
