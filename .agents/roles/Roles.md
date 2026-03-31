# Roles Routing Rules (HabitTracker)

Назначение: автоматический выбор роли и соответствующих skills для задач проекта HabitTracker
(Android + Kotlin + Jetpack Compose + Room + Firestore + WorkManager + FCM).

## Базовые правила

1. Сначала определить интент задачи, затем роль(и), затем skills.
2. На один шаг роли использовать 2-3 primary skills.
3. При явном указании роли/skills пользователем это имеет приоритет над авто-роутингом.
4. Если интент неочевиден, использовать fallback: `fullstack-guardian` + `debugging-strategies`.

## Роли и skills

### 1) Architect
Для: архитектура, границы слоёв, sync-подсистема, data flow, ADR.
Primary:
- `architecture-designer`
- `architecture-patterns`
- `architecture-decision-records`
Secondary:
- `api-designer`
- `api-design-principles`
- `openapi-spec-generation`

### 2) Orchestrator
Для: декомпозиция задач, этапы, зависимости, handoff между ролями/агентами.
Primary:
- `task-coordination-strategies`
- `team-communication-protocols`
- `team-composition-patterns`
Secondary:
- `debugging-strategies`
- `code-review-excellence`

### 3) Coder
Для: реализация фич и фиксов в Android/Kotlin/Compose, sync и data-слой.
Primary:
- `kotlin-specialist`
- `fullstack-guardian`
- `react-expert`
Secondary:
- `auth-implementation-patterns`
- `sql-optimization-patterns`
- `react-state-management`

### 4) Analyst
Для: root cause, анализ метрик/логов, производительность, проблемы синхронизации.
Primary:
- `monitoring-expert`
- `debugging-wizard`
- `debugging-strategies`
Secondary:
- `data-storytelling`
- `kpi-dashboard-design`
- `sql-optimization-patterns`

### 5) Designer
Для: UI/UX-концепт, визуальная система, motion, consistency с Tahoe стилем.
Primary:
- `mobile-android-design`
- `visual-design-foundations`
- `interaction-design`
Secondary:
- `design-system-patterns`
- `frontend-design`
- `canvas-design`

### 6) Layout Engineer
Для: практическая вёрстка экранов/компонентов, адаптивность, компонентная структура.
Primary:
- `mobile-android-design`
- `web-component-design`
- `responsive-design`
Secondary:
- `tailwind-design-system`
- `frontend-design`

### 7) Reviewer
Для: review изменений, регрессии, безопасность, риски в sync/данных.
Primary:
- `code-review-excellence`
- `code-reviewer`
- `security-reviewer`
Secondary:
- `auth-implementation-patterns`
- `accessibility-compliance`

### 8) QA / Tester
Для: test strategy, e2e/flows, flaky-сценарии, smoke/regression.
Primary:
- `e2e-testing-patterns`
- `playwright-expert`
- `webapp-testing`
Secondary:
- `code-reviewer`

## Intents -> Roles

- "архитектура", "перестроить слой", "как спроектировать" -> Architect
- "план", "разбей на этапы", "что делать по шагам" -> Orchestrator
- "реализуй", "добавь", "пофикси" -> Coder
- "проанализируй", "почему сломалось", "найди причину" -> Analyst
- "улучши дизайн", "сделай красивее", "UX" -> Designer
- "сверстай", "сделай экран", "адаптив" -> Layout Engineer
- "сделай ревью", "проверь безопасность", "аудит" -> Reviewer
- "напиши тесты", "e2e", "проверь сценарии" -> QA / Tester

## Multi-role pipeline

Для сложных задач использовать цепочку:
1. Architect (дизайн решения + ограничения)
2. Orchestrator (план реализации)
3. Coder (изменения)
4. Reviewer или QA (проверка)

Обязательно подключать Reviewer/QA для критичных зон:
- sync/outbox/merge/LWW/pending guards
- auth и доступ к данным
- миграции/удаления/изменения схем
- уведомления и background jobs
