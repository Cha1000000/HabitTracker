# Sync Gaps & Risks (Outbox + Firestore Realtime)

Документ фиксирует текущее состояние синхронизации офлайн‑фёрст и выявленные пробелы.

## 1) Покрытые realtime-слушатели (Firestore -> Room)
- Friend Requests: incoming/outgoing — in `SocialRepositoryImpl` (callbackFlow, upsert в Room)
- Friendships: обе стороны — in `SocialRepositoryImpl`
- Habit Invitations:
  - Incoming (`toUserId`) — in `SocialRepositoryImpl`
  - Outgoing (`fromUserId`) — in `SocialRepositoryImpl`
  - By Habit (`habitId`) — in `SocialRepositoryImpl`
- Habit Entries:
  - По привычке, по пользователю, и по паре (habitId+userId) — in `HabitEntryRepositoryImpl`
- Habits:
  - Источник: `FirestoreRepositoryImpl.getUserHabits()`
  - Merge в Room: `HabitRepositoryImpl.startObservingRemoteHabits()` (с проверкой pending outbox)
- User:
  - `UserRepositoryImpl.observeUser()` (Firestore listener -> Room, с pending-guard)
- GroupHabitNotifications:
  - `GroupHabitNotificationRepositoryImpl` (Firestore listener -> Room, sticky read-state + pending-guard)
- Challenges:
  - `ChallengeRepositoryImpl.observeChallengesForUser()` (Firestore listeners -> Room, LWW по `updatedAtMillis`)

## 2) Покрытые Room-first + Outbox (запись в Firestore с очередью)
- Friend Requests: send/accept/decline/cancel — `SocialRepositoryImpl` + `SyncManager`
- Friendships: add/remove/block/unblock — `SocialRepositoryImpl` + `SyncManager`
- Habit Invitations: send/accept/decline/cancel — `SocialRepositoryImpl` + `SyncManager`
  - Примечание: обновление `participants` в accept делается прямым Firestore update (сложная операция, не через outbox)
- Habits: create/update/delete — `HabitRepositoryImpl` + `SyncManager`
- HabitParticipantSettings: upsert — `HabitRepositoryImpl.updateHabitParticipantSettings()` + `SyncManager`
- Challenges: create/update/delete — `ChallengeRepositoryImpl` + `SyncManager`

## 3) Предположительно непокрытые/частично покрытые зоны
- Users:
  - Google Sign-In / первичное создание профиля и консистентность `fcmToken` между устройствами требуют отдельного формального сценария (как источник истины и когда обновляем)
- Achievements:
  - DAO есть, но нет realtime-слушателей и Outbox путей
- Notifications:
  - Realtime источник для group-notifications есть (`GroupHabitNotificationRepositoryImpl`), но нужно следить за политикой удаления/архивации на сервере (сейчас Outbox delete для этой сущности не внедрён)
- FirestoreRepository (domain):
  - Интерфейс присутствует, но реальная интеграция с текущими репозиториями частично/неединая; дублирование ответственности с outbox/слушателями

## 4) Технические риски и пробелы
- Конфликты и LWW:
  - Не везде строгая нормализация по `updatedAt`: часть сущностей хранит `updatedAt` как `LocalDate` (низкая точность), часть — как `updatedAtMillis`.
  - Для Habits merge идёт по сравнению дат (startOfDay), что может быть недостаточно при быстрых правках.
- Idempotency:
  - Для Outbox-операций не описаны идемпотентные ключи; риск двойной записи/дубликатов при ретраях
- Deletes (tombstones):
  - Сейчас outbox DELETE делает hard delete. Для мульти-девайс сценариев это может быть ок, но для “доставки удаления” после долгого офлайна лучше tombstone (`deletedAt`).
- Индексы Firestore:
  - Для сложных whereEqualTo комбинаций (e.g., habit_entries с двумя полями) индексы должны быть проверены/зафиксированы
- Error handling:
  - Слушатели Firestore проглатывают ошибки (минимальные логи). Нужна централизованная обработка и метрики
- Battery/Data usage:
  - Множественные слушатели могут быть затратны. Требуются правила отписки/подписок по жизненному циклу экранов и скоупов
- Pending-guard coverage:
  - Не во всех listener'ах есть pending-guard (например, в `ChallengeRepositoryImpl.observeChallengesForUser()` он отсутствует).
- ParticipantSettings merge:
  - `ParticipantSettingsRepositoryImpl.get()` при расхождении содержимого предпочитает remote, что теоретически может перетирать локальные изменения, если они ещё не ушли outbox.

## 5) Предлагаемый план закрытия
1. Habits realtime + Outbox
   - Унифицировать `updatedAtMillis` в Firestore, чтобы LWW был надёжным
2. Users + Google Sign-In
   - На первый signin/create/update юзера в Firestore и Room
   - FCM tokens: хранить массив, де-дуп, удалять старые токены
   - (Опц.) Реалтайм-слушатель профиля текущего user в Room
3. Challenges/Achievements
   - Добавить pending-guard в listeners для challenges
   - Achievements: добавить Outbox + realtime
4. Deletes Policy
   - Ввести soft-delete (поле `deletedAt`) и консистентный merge
5. Idempotency & LWW
   - Ввести `opId` для Outbox и обязательные `updatedAt` для всех документов
6. Observability
   - Логи (без чувствительных данных), счётчики drain’а Outbox, дым-тесты ошибок слушателей

## 6) Маппинг путей чтения/записи (кратко)
- Room-first чтение: UI подписан на DAO Flow
- Firestore -> Room: все реализованные коллекции обновляют Room через слушателей
- Room-first запись: upsert в Room, затем Outbox enqueue для Firestore; исключения — сложные операции (напр. participants array)

## 7) Требуемые доработки в коде
- `ChallengeRepositoryImpl.observeChallengesForUser()`: pending-guard на remote -> Room + reconcile-delete должен пропускать pending
- `UserRepository`: Google Sign-In pathway + realtime (опционально)
- `AchievementRepository`: Outbox + realtime
- Централизовать `updatedAt` и idempotency ключи в `SyncManager`

Статус: документ обновляется по мере закрытия пунктов плана.
