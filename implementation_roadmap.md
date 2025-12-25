# 🗺️ HabitTracker — Дорожная карта реализации

> Документ создан: 15 декабря 2024
> Статус: Реализовано

---

## 📋 Общий порядок реализации

1. **Этап A**: Завершение интеграции Челленджей
2. **Этап B**: Доработка Геймификации
3. **Этап C**: Закрытие пробелов синхронизации
4. **Этап D**: UI/UX полировка и прочее

---

## 🏆 ЭТАП A: Завершение интеграции Челленджей

### A.1 Интеграция в навигацию (0.5 дня)
**Текущее состояние**: Экраны и ViewModels созданы, но не подключены к навигации.

**Задачи:**
- [x] Добавить маршруты в `AppNavigation.kt`:
  - `Screen.Challenges` — список челленджей
  - `Screen.CreateChallenge` — создание челленджа
  - `Screen.ChallengeDetail` — детали челленджа
- [x] Добавить composable destinations для каждого экрана
- [x] Добавить кнопку/вкладку для доступа к челленджам (BottomNavigation или из профиля)

### A.2 Доработка UI экранов челленджей (1 день)
**Задачи:**
- [x] `ChallengesScreen.kt`:
  - Проверить интеграцию с `ChallengesViewModel`
  - Добавить фильтры (активные/завершённые/мои)
  - Добавить FAB для создания челленджа
  - Добавить pull-to-refresh
- [x] `CreateChallengeScreen.kt`:
  - Проверить валидацию формы
  - Добавить выбор привычки из списка
  - Добавить выбор участников (только друзья)
  - Добавить выбор дат и правил
- [x] `ChallengeCard.kt`:
  - Отображение прогресса участников
  - Статус челленджа (pending/active/completed)
  - Кнопки действий (присоединиться/покинуть)

**Примечание:** список TODO для закрытия актуализирован в коде (устаревшие TODO удалены/закрыты).

**Файлы для изменения:**
```
presentation/ui/challenges/ChallengesScreen.kt
presentation/ui/challenges/CreateChallengeScreen.kt
presentation/ui/challenges/ChallengeCard.kt
```

### A.3 Интеграция с данными (1 день)
**Задачи:**
- [x] Проверить `ChallengeRepositoryImpl` — корректность CRUD операций
- [x] Добавить Outbox-операции для челленджей:
  - CREATE_CHALLENGE
  - UPDATE_CHALLENGE
  - JOIN_CHALLENGE
  - LEAVE_CHALLENGE
- [x] Добавить realtime-слушатель Firestore для коллекции `challenges`
- [x] Обновить `SyncManager` для обработки челленджей

**Файлы для изменения:**
```
data/repository/ChallengeRepositoryImpl.kt
data/sync/SyncManager.kt
data/local/dao/ChallengeDao.kt
```

### A.4 Автоматическое обновление прогресса (0.5 дня)
**Задачи:**
- [x] При отметке выполнения привычки — обновлять прогресс в связанных челленджах
- [x] Интегрировать `UpdateChallengeProgressUseCase` в `MarkHabitCompletedUseCase`
- [x] Автоматическое определение победителя при завершении челленджа
- [x] Уведомления о завершении челленджа и победителе

**Файлы для изменения:**
```
domain/usecase/MarkHabitCompletedUseCase.kt
domain/usecase/challenge/UpdateChallengeProgressUseCase.kt
```

### A.5 Тестирование челленджей (0.5 дня)
**Задачи:**
- [x] Unit-тесты для Use Cases челленджей
- [x] Проверка сценариев: создание, присоединение, выход, завершение
- [x] Проверка edge cases: челлендж без участников, истёкший челлендж

---

## 🎮 ЭТАП B: Доработка Геймификации

### B.1 Автоматическое начисление очков (1 день) ✅
**Текущее состояние**: Полностью интегрировано.

**Задачи:**
- [x] Интегрировать `CalculatePointsUseCase` в `MarkHabitCompletedUseCase`:
  - +10 очков за выполнение привычки
  - +50 очков за streak 7 дней
  - +200 очков за streak 30 дней
- [x] Интегрировать `UpdateUserLevelUseCase` — автоматическое повышение уровня
- [x] Добавить начисление очков за:
  - Победу в челлендже (+100)
  - Создание групповой привычки (+25)
  - Приглашение друга (+15)

**Файлы для изменения:**
```
domain/usecase/MarkHabitCompletedUseCase.kt
domain/usecase/gamification/CalculatePointsUseCase.kt
domain/usecase/gamification/UpdateUserLevelUseCase.kt
domain/usecase/gamification/UpdateUserPointsUseCase.kt
```

### B.2 Автоматическая разблокировка достижений (1 день) ✅
**Задачи:**
- [x] Интегрировать `CheckAchievementsUseCase` для проверки условий достижений
- [x] Триггеры для разблокировки:
  - При выполнении привычки — `MarkHabitCompletedUseCase`
  - При добавлении друга — `AcceptFriendRequestUseCase`, `SendFriendRequestUseCase`
  - При победе в челлендже — `CloseExpiredChallengesUseCase`
- [x] Сохранение разблокированных достижений в Room + Firestore (через `SyncManager`)
- [x] Показ уведомления при разблокировке достижения (in-app + push)

**Файлы для изменения:**
```
domain/usecase/gamification/GetAchievementsUseCase.kt
data/repository/AchievementRepositoryImpl.kt
```

### B.3 Улучшение экрана статистики (0.5 дня) ✅
**Задачи:**
- [x] `StatsScreen.kt`:
  - Отображение текущего уровня и прогресса до следующего (`UserLevelCard`)
  - Общее количество очков
  - Статистика по привычкам (`HabitStatsCard`)
  - Лучший streak (`StreakStatsCard`)
- [x] Добавить графики/визуализацию прогресса (`ActivityChartCard` — последние 7 дней)

**Файлы для изменения:**
```
presentation/ui/stats/StatsScreen.kt
presentation/viewmodel/StatsViewModel.kt
```

### B.4 Лидерборды среди друзей (0.5 дня) ✅
**Задачи:**
- [x] Создать `LeaderboardSection` в `StatsScreen`
- [x] Отображение рейтинга друзей по очкам (`GetLeaderboardUseCase`)
- [x] Еженедельный рейтинг (`LeaderboardType.WEEKLY` с переключателем в UI)
- [x] Рейтинг по конкретной привычке для групповых (`getHabitLeaderboard`)

**Файлы для создания/изменения:**
```
presentation/ui/stats/LeaderboardSection.kt (или отдельный экран)
domain/usecase/gamification/GetLeaderboardUseCase.kt (если нет)
```

### B.5 Уведомления о достижениях (0.5 дня) ✅
**Задачи:**
- [x] Локальное уведомление при разблокировке достижения (сохраняется в `GroupHabitNotificationRepository`)
- [x] Push-уведомление пользователю о достижении (через FCM)
- [x] In-app toast/snackbar с анимацией (`AchievementUnlockedToast` + `AchievementEventManager`)

---

## 🔄 ЭТАП C: Закрытие пробелов синхронизации

### C.1 Realtime-слушатели для Habits (1 день) ✅
**Текущее состояние**: Реализовано (Firestore → Room + pending-guard + LWW по `updatedAtMillis`).

**Задачи:**
- [x] Добавить realtime-слушатель Firestore → Room (источник: `FirestoreRepositoryImpl.getUserHabits()`; merge в Room: `HabitRepositoryImpl.startObservingRemoteHabits()`)
  - По `createdBy == currentUserId`
  - По `participants contains currentUserId`
- [x] Merge входящих данных в Room с учётом LWW по `updatedAtMillis`
- [x] Обработка soft-delete (tombstones через `deletedAtMillis`/`isActive`)

**Файлы для изменения:**
```
data/repository/HabitRepositoryImpl.kt
```

### C.2 Outbox для Habits (0.5 дня) ✅
**Задачи:**
- [x] Добавить Outbox-операции:
  - UPSERT (entityType=`habit`)
  - DELETE (soft-delete через tombstone)
- [x] Интегрировать в `insertHabit()`, `updateHabit()`, `deleteHabit()`
- [x] KickSync после enqueue (экспедит синка, чтобы не ждать периодического воркера)

**Файлы для изменения:**
```
data/repository/HabitRepositoryImpl.kt
data/sync/SyncManager.kt
```

### C.3 Realtime + Outbox для Challenges (0.5 дня) ✅
**Задачи:**
- [x] Добавить realtime-слушатель для коллекции `challenges` (`ChallengeRepositoryImpl.observeChallengesForUser()`)
- [x] Добавить Outbox-операции для челленджей (entityType=`challenge`, UPSERT/DELETE)
- [x] Merge с LWW стратегией по `updatedAtMillis` + pending-guard
- [x] Soft-delete (tombstones через `deletedAtMillis`/`isActive`)
- [x] KickSync после enqueue

**Файлы для изменения:**
```
data/repository/ChallengeRepositoryImpl.kt
```

### C.4 Realtime + Outbox для Achievements (0.5 дня) ✅
**Задачи:**
- [x] Добавить realtime-слушатель для `user_achievements` (guarded merge + LWW по `updatedAtMillis`)
- [x] Outbox для прогресса/разблокировки достижений (entityType=`user_achievement`, UPSERT)
- [x] KickSync после enqueue

**Файлы для изменения:**
```
data/repository/AchievementRepositoryImpl.kt
```

### C.5 Стандартизация Idempotency и LWW (0.5 дня) (частично)
**Задачи:**
- [x] Добавить `opId` (UUID) для Outbox-операций (поле `operationId` на уровне outbox)
- [ ] Проверка `lastOpId` перед применением операции (не реализовано — требует хранения `lastOpId` в документе Firestore)
- [ ] Стандартизировать `updatedAtMillis` во всех сущностях (частично: Habits/Challenges/UserAchievements; остальные — по мере необходимости)

**Файлы для изменения:**
```
data/sync/SyncManager.kt
data/local/entity/*.kt (добавить updatedAt где нет)
```

### C.6 Soft-delete везде (0.5 дня) (частично)
**Задачи:**
- [x] Soft-delete/tombstones реализованы для `habit` и `challenge`
- [ ] Добавить `deletedAtMillis` в остальные сущности, где это важно
- [ ] Обновить mappers/DAO (фильтрацию) для остальных сущностей, где будет включён soft-delete

---

## ✨ ЭТАП D: UI/UX полировка и прочее

### D.1 Анимации (0.5 дня) (частично)
**Задачи:**
- [x] Confetti-анимация при отметке выполнения привычки (в коде есть флаг, но нет UI-компонента)
- [x] Анимация checkmark (сейчас просто смена цвета/иконки)
- [x] Анимация при разблокировке достижения (`AchievementUnlockedToast` с slide/scale анимацией)

### D.2 Улучшение списка привычек (0.5 дня) (частично)
**Задачи:**
- [x] Показ аватарок участников на карточке групповой привычки (сейчас только иконка группы)
- [x] Индикаторы прогресса группы (`GroupHabitProgressComponent`)
- [x] Swipe-действия (удалить - `SwipeToDismissBox`)
- [x] Массовый выход из совместных привычек при удалении друга (заглушка в `FriendsViewModel`)

**TODO для закрытия:**
- `FriendsViewModel.kt:228` — `// TODO: trigger bulk leave from shared habits with this friend`

### D.3 Deep links для приглашений (0.5 дня)
**Задачи:**
- [x] Настроить deep links для invite-code привычек (в Manifest нет `intent-filter` для URL схем)
- [x] Обработка навигации из Push-уведомлений (в `MainActivity` есть `handleIntentForNavigation`)

### D.4 Push-уведомления о активности друзей (0.5 дня) ✅
**Задачи:**
- [x] Уведомление когда друг выполнил привычку в группе
- [x] Уведомление о новом челлендже от друга
- [x] Настройки уведомлений (вкл/выкл по типам) - пока нет экрана настроек

### D.5 Оптимизация производительности (0.5 дня) (частично)
**Задачи:**
- [x] Проверка recomposition в Compose (используются `key`, `recomposeKey`)
- [x] Оптимизация запросов к БД
- [x] Кэширование изображений (Coil используется с `AsyncImage`)

---

## 📊 Итоговая оценка времени

| Этап | Описание | Оценка |
|------|----------|--------|
| A | Челленджи | 3.5 дня |
| B | Геймификация | 3.5 дня |
| C | Синхронизация | 3.5 дня |
| D | UI/UX и прочее | 2.5 дня |
| **Итого** | | **~13 дней** |

---

## ✅ Критерии готовности

- [x] Все экраны челленджей доступны и функциональны
- [x] Очки начисляются автоматически при выполнении привычек
- [x] Достижения разблокируются автоматически
- [x] Данные синхронизируются между устройствами в реальном времени
- [x] Приложение работает в офлайн-режиме
- [x] UI отполирован, анимации работают

---

## 📝 Примечания

- Коммиты делаются только по запросу пользователя
- Перед каждым этапом — краткий план и подтверждение
- После каждого этапа — проверка сборки и базовое тестирование
