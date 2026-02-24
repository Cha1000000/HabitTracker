# Roles Quick Start (HabitTracker)

Используйте `Roles.md` для авто-роутинга ролей и skills.

## Базовый шаблон промпта

`Role: <RoleName>. Skills: <skill1>, <skill2>. Task: <что сделать>. Output: <ожидаемый результат>. Constraints: <ограничения>.`

Пример:
`Role: Architect. Skills: architecture-designer, architecture-patterns. Task: переработать sync-поток Room->Outbox->Firestore. Output: ADR + риски + план миграции. Constraints: без breaking changes.`

## Шаблоны по ролям

### Architect
`Role: Architect. Skills: architecture-designer, architecture-patterns, architecture-decision-records. Спроектируй решение для <feature>.`

### Orchestrator
`Role: Orchestrator. Skills: task-coordination-strategies, team-communication-protocols. Разбей <feature> на этапы и зависимости.`

### Coder
`Role: Coder. Skills: kotlin-specialist, fullstack-guardian. Реализуй <feature>. Output: код + тесты + краткий changelog.`

### Analyst
`Role: Analyst. Skills: monitoring-expert, debugging-wizard, debugging-strategies. Найди root cause для <issue>.`

### Designer
`Role: Designer. Skills: mobile-android-design, visual-design-foundations, interaction-design. Обнови UX/UI экрана <screen>.`

### Layout Engineer
`Role: Layout Engineer. Skills: mobile-android-design, web-component-design, responsive-design. Собери экран <screen> с акцентом на читаемость и адаптив.`

### Reviewer
`Role: Reviewer. Skills: code-review-excellence, code-reviewer, security-reviewer. Проверь diff на регрессии и риски.`

### QA / Tester
`Role: QA. Skills: e2e-testing-patterns, playwright-expert, webapp-testing. Подготовь и пройди test-сценарии для <flow>.`

## Multi-role цепочка

`Use pipeline: Architect -> Orchestrator -> Coder -> Reviewer.`

Пример:
`Task: улучшить надёжность sync при конфликте локальных и удалённых апдейтов.`
`Output: 1) дизайн, 2) план, 3) код, 4) review findings.`

## Override

Если роль/skills явно указаны в промпте, это приоритетнее авто-роутинга.
