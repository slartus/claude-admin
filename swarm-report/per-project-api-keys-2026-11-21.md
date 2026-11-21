# Per-project API keys — отчёт

Дата: 2026-11-21
Профиль: dev (фича в проекте с кодом)
Проект: claude-admin (Kotlin/Compose Desktop, `:domain` `:data` `:presentation` `:app`)

## Запрос
«Возможность задавать API-ключи для каждого проекта свои — как для opencode, так и для claude (deepseek и др.)» через интерфейс claude-admin.

## Решение
В вкладке Detали проекта (DetailsView, вкладка PROJECT) добавлена карточка **API Keys** с формой пресетов провайдеров (apiKey + baseUrl + model). Ключи пишутся в файлы проекта:

- **Claude** → `.claude/settings.local.json` (`env.ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN`, опционально `ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL`).
- **OpenCode** → `.opencode.json` / `opencode.json` (`providers.<id>.settings.apiKey` / `settings.baseURL`).

Пресеты Claude: Anthropic, DeepSeek (`https://api.deepseek.com/anthropic`, `deepseek-chat`, `ANTHROPIC_AUTH_TOKEN`), OpenRouter (`ANTHROPIC_AUTH_TOKEN`). Пресеты OpenCode: Anthropic, OpenAI, DeepSeek, OpenRouter + «Произвольный».

## Архитектура
Repository → UseCase → RootComponent(StateFlow) → Composable, DI через Koin.

| Слой | Файл | Роль |
|---|---|---|
| domain | `model/ApiKeys.kt` | `ClaudeApiConfig`, `OpenCodeProviderConfig`, `ProjectApiKeys` |
| domain | `repository/ProjectApiKeysRepository.kt` | интерфейс `load`/`save` |
| domain | `usecase/SaveProjectApiKeysUseCase.kt` | `Result<Unit>`, rethrow `CancellationException` |
| domain | `usecase/LoadProjectDetailsUseCase.kt`, `model/ProjectDetails.kt` | поле `apiKeys` в деталях |
| data | `apikeys/FileProjectApiKeysRepository.kt` | файловое чтение/запись |
| presentation | `root/RootComponent.kt`, `root/RootState.kt` | `saveProjectApiKeys`, state сохранения |
| app | `ui/details/ApiKeysCard.kt`, `ui/details/DetailsView.kt`, `ui/RootScreen.kt`, `Main.kt`, `di/AppModule.kt` | UI + DI |

## Безопасность
- Секреты не логируются: `toString()` у `ClaudeApiConfig`, `OpenCodeProviderConfig` маскирует ключи (`<redacted>`).
- Ключи пишутся в `settings.local.json` (не в `settings.json`).
- Используются env-переменные для ключей; в файл пишется само значение.

## Селф-ревью отдельным агентом
Ревьюер (general) нашёл 2 MAJOR и ряд MINOR. Исправлено итеративно:

1. **MAJOR** — порядок приоритета файлов opencode был обратным референсу. Исправлено: сначала `.opencode.json`, затем `opencode.json`; при отсутствии обоих создаётся `.opencode.json`.
2. **MAJOR** — устаревший reload после сохранения (перезапись деталей чужого проекта при смене selection во время сохранения). Исправлено: reload и сообщение «Ключи сохранены» только если selection всё ещё указывает на этот проект; сброс `apiKeysSaving`/`apiKeysSaveMessage` при `selectProject`.
3. **MINOR** — потеря всех провайдеров opencode, не попавших в список UI (напр. кастомные `npm`/`models`). Закрыто: загрузчик теперь включает ВСЕ провайдеры файла (и бесключевые), поэтому `retainAll` удаляет только реально удалённые из формы.
4. **MINOR** — при пустом ключе удалялся только выбранный env-var, вторая переменная оставалась. Теперь удаляются обе ключевые переменные.
5. **MINOR** — `JsonNull`/нестрока в `ANTHROPIC_API_KEY` давали `"null"` вместо пустого. `contentOrBlank` теперь учитывает `isString`.
6. **MINOR** — `runCatching` проглатывал `CancellationException`. Заменено на явный `try/catch` с rethrow.
7. **MINOR** — некорректная подпись «Anthropic» в дропдауне для ручного/кастомного конфига. Добавлен фолбэк лейбла «Manual».
8. **MINOR** — маскирование значений в `toString()` доменных моделей.

## Тесты
`data/src/test/.../apikeys/FileProjectApiKeysRepositoryTest.kt` — 11 тестов: пустая загрузка; чтение Claude env + opencode providers; детект `ANTHROPIC_AUTH_TOKEN`; сохранение Claude с сохранением прочих ключей; смена key-env-var при смене пресета; создание файлов + round-trip; сохранение opencode с сохранением посторонних полей и удалением отсутствующих; очистка Claude env при пустом ключе; пропуск пустого providerId; выживание бесключевого кастомного провайдера; приоритет `.opencode.json` при наличии обоих файлов. Все проходят.

Проверки: `./gradlew test` — OK (включая компиляцию всех модулей). Ошибок компиляции нет.

## Статус
Фича реализована и протестирована. Коммит не делался (по правилам).
