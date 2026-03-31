# HabitTracker Project Context for AI Agents

## Role Routing Rules

For automatic role selection and matching skill selection, use:
`/Users/racerkafa/Documents/MyProjects/Android/Compose/HabitTracker/.agents/roles/Roles.md`

Priority order:
1. Explicit user role/skills in prompt.
2. Role auto-routing rules from `Roles.md`.
3. Project conventions from this AI context file.


This file provides a comprehensive overview of the HabitTracker project context, architecture, and logic to quickly onboard AI models and agents.

## 1. Project Overview
**HabitTracker** is a native Android application designed to make habit building engaging through social cooperation and gamification.
*   **Goal**: Enable users to create shared habits, track progress together, challenge friends, and earn rewards.
*   **Key Features**: Personal/Group habits, Friends system, Challenges, Leaderboards, Rich Gamification (Points, Levels, Achievements).

## 2. Tech Stack & Ecosystem
*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Design System**: Custom "Tahoe" Design System (Glassmorphism style, custom components).
*   **Architecture**: Clean Architecture (Presentation layer -> Domain layer -> Data layer) + MVVM.
*   **Dependency Injection**: Hilt.
*   **Concurrency**: Coroutines + StateFlow (Reactive programming).
*   **Local Database**: Room (SQLite) - **Single Source of Truth for UI**.
*   **Remote Database**: Firebase Firestore.
*   **Notifications**: Firebase Cloud Messaging (FCM) + WorkManager (Local).
*   **Background Jobs**: WorkManager.
*   **Analytics/Crashlytics**: Firebase.

## 3. Project Structure
```
com.habittracker/
├── presentation/   # UI, ViewModels, State, Navigation, Theme
├── domain/         # Business Logic, Models, Repository Interfaces, UseCases
├── data/           # Repository Implementations, Room Entities, Firestore DTOs, Workers, Sync Logic
└── di/             # Hilt Modules
```

## 4. Core Logic & Nuances

### 4.1. Synchronization Architecture (CRITICAL)
The project uses a custom **Offline-first Outbox + Realtime** synchronization strategy.
*   **Room-First**: The UI *always* observes the local Room database. No direct Firestore calls from UI.
*   **Write Flow**:
    1.  User action -> Repository.
    2.  Repository updates Room immediately (UI reflects change).
    3.  Repository enqueues operation in `sync_outbox` table (Room).
    4.  `SyncManager` (triggered via WorkManager or "KickSync") reads outbox and pushes to Firestore.
*   **Read Flow**:
    1.  Firestore Realtime Listeners receive updates.
    2.  Updates are merged into Room.
*   **Conflict Resolution**:
    *   **Pending Guard**: Incoming remote changes are **ignored** if there is a pending/in-progress Outbox operation for that entity (prevents remote state from overwriting local un-synced changes).
    *   **Last-Write-Wins (LWW)**: Uses `updatedAtMillis` for merging conflicts.
    *   **Soft Deletes**: `Habit` and `Challenge` use tombstones (`deletedAtMillis`, `isActive=false`) instead of hard deletes to ensure deletion propagates to other devices.

### 4.2. Data Models
*   **Habit**: Can be `PERSONAL` or `GROUP`. Group habits have `participants`. Has `goalType` (`SIMPLE` checkbox or `MEASURABLE` numeric value).
*   **HabitEntry**: Daily record of a habit. Contains `value` (for measurable habits), `completed`, `streakBonus`.
*   **User**: Contains gamification stats (`level`, `points`, `streak`).
*   **Challenge**: Competitive wrapper around habits. Has rules (`ChallengeRules`), start/end dates, and status.

### 4.3. Gamification
*   **Points**: Awarded for completions (+10), perfect days (+50), social actions.
*   **Levels**: Linear progression (1000 points per level). Titles: Novice -> Enthusiast -> Expert -> Master -> Legend.
*   **Streaks**: Bonus points for 3, 7, 30 day streaks.

### 4.4. Notifications
*   **Local**: Scheduled via `WorkManager` for habit reminders. Smart "Follow-up" reminders check if habit is done before notifying.
*   **Push (FCM)**: Social events (Friend Requests, Group Invites, Challenge updates).
    *   *Note*: `SendGroupHabitPushNotificationUseCase` handles notifying participants when a group habit is completed.

## 5. Important Reference Documents
Detailed technical guides are available in the `guides_rules_instructions` directory:

*   **Synchronization Logic**: [sync_architecture.md](sync_architecture.md) - **Read this for deep dive into sync/outbox logic.**
*   **Known Issues/Gaps**: [sync_gaps.md](sync_gaps.md) - Current limitations and risks in sync.
*   **Push Notifications**: [FCM_SETUP_INSTRUCTIONS.md](FCM_SETUP_INSTRUCTIONS.md) - Setup and architecture of FCM.
*   **Analytics**: [ANALYTICS_GUIDE.md](ANALYTICS_GUIDE.md) - Events, crash monitoring, and performance.

## 6. Development Rules (Short)
*   **Kotlin**: Use extension functions, sealed classes, nullable safety.
*   **Compose**: Minimize recomposition, use `Tahoe` components.
*   **Data**: Always go through UseCases. Never access Repository directly from View.
*   **Commits**: Follow standard convention, English only.

# Общие правила при формировании ответов

- Отвечай на русском языке!
- Рассуждай пошагово, прежде чем дать ответ.
- Используй sequential thinking для сложных размышлений и сложно поставленных задач.
- Не выдавай очень высокоуровневые ответы, твоя задача дать конкретное решение применимое к проекту.
- Используй Context7 для доступа к документации всех библиотек.
- Для реализации любых фич с использованием интеграций с внешними api/библиотеками изучай документации с помощью context7 инструментов.
- Не давай слишком детальные пояснения, но описывай зачем ты вносишь данные изменения.
- Перед тем как внести изменения, опиши общий план реализации по пунктам и после спроси с какого пункта будем продолжать реализацию.
- Чем меньше строк кода, тем лучше. Используй подход оптимизированного написания эффективного кода.
- Действуй как старший Android разработчик и крутой, опытный UX/UI дизайнер.
- Если (когда) тебе нужно сгенерировать изображение, используй openai-gpt-image-mcp tool с quality установленным в "low", или nano-banana mcp tool. Перед генерацией убедись в существовании file_output folder; в противном случае сгенерированные изображения не будут сохранены.

# Глобальные правила для разработки

## Общие принципы кода (для Kotlin)
- Используй чистый, читаемый код с осмысленными именами переменных и функций
- Следуй принципам SOLID и DRY
- Пиши код на английском языке (комментарии могут быть на русском)
- Предпочитай композицию наследованию
- Используй nullable safety везде, где это возможно
- По окончании внесения изменений в файлы с Kotlin кодом проверяй наличие неиспользуемых импортов, и при их наличии удаляй их
- Аналогично проверяй переменные, методы, аргументы методов и конструкторов классов на их актуальность - если они нигде больше не используются (например после рефакторинга), или потенциально уже не будут нужны - так же удаляй их. Оставляй после себя чистый код.
- При внесённых изменений и пересборке проекта проверяй наличие появившихся deprecated методов и т.п. артефактов и предлагай на что более актуальное их можно безопасно заменить
- При сборках проекта обращай внимание на ворнинги и те, которые можно быстро и безопасно пофиксить - предлагай как, а те, которые не особо критичны, или могут возникать например от эмулятора, или IDE специфичных нюансов - можешь игнорировать

## Структура ответов
- Объясняй логику изменений перед кодом
- Показывай полный контекст файла при изменениях
- Предлагай альтернативные решения для сложных задач
- Указывай на потенциальные проблемы производительности

## Безопасность
- Всегда валидируй входные данные
- Для логирования используй Timber вместо стандартного Log.*
- Не логируй чувствительную информацию
- Используй безопасные методы для работы с сетью
- Не оставляей пустые блоки catch (_: Exception) { }, лучше по возможно заполнять их логированием непредвиденных ошибок
- Не делай коммиты самостоятельно, пока я сам не попрошу
- Оформляй коммиты по следующему стандарту: https://gist.github.com/tonibardina/9290fbc7d605b4f86919426e614fe692#file-std-md 
- Коммит-месседж делай только на английском языке

## Тестирование
- При написании тестов, не старайся подогнать их под текущую реализацию тестируемых кейсов (вдруг они ошибочны?), а пиши тесты так, что бы они покрывали задуманную (ожидаемую) логику/поведение тестируемого функционала.
- При запуске тестов, если они падают, не от ошибок в синтаксисе, а именно из-за не прохождения логики (сценария) - не старайся подогнать сразу под текущую реализацию, а сперва проверь на корректность саму логику (и реализацию) тестируемого кейса. Если проблема в логие - исправь её, а если с логикой всё ОК, тогда исправляй сам тест.
