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

## 2) Покрытые Room-first + Outbox (запись в Firestore с очередью)
- Friend Requests: send/accept/decline/cancel — `SocialRepositoryImpl` + `SyncManager`
- Friendships: add/remove/block/unblock — `SocialRepositoryImpl` + `SyncManager`
- Habit Invitations: send/accept/decline/cancel — `SocialRepositoryImpl` + `SyncManager`
  - Примечание: обновление `participants` в accept делается прямым Firestore update (сложная операция, не через outbox)

## 3) Предположительно непокрытые/частично покрытые зоны
- Habits (коллекция привычек):
  - Нет явных realtime-слушателей Firestore -> Room для `Habit` (из кода репо)
  - Нет унифицированного Outbox для create/update/delete Habit (возможно есть локальные операции через `HabitDao`, но без Firestore записи)
- Users:
  - Реалтайм-слушателя для `User` (Firestore -> Room) не обнаружено
  - Google Sign-In: создание/апдейт user-документа и массив FCM-токенов — PENDING
- Challenges:
  - Есть модели/DAO/репо, но нет realtime-слушателей и Outbox путей
- Achievements:
  - DAO есть, но нет realtime-слушателей и Outbox путей
- Notifications (локальная очередь FCM):
  - Локальная очередь есть, но отсутствует стратегия консистентности при multi-device (нет Firestore источника)
- FirestoreRepository (domain):
  - Интерфейс присутствует, но реальная интеграция с текущими репозиториями частично/неединая; дублирование ответственности с outbox/слушателями

## 4) Технические риски и пробелы
- Конфликты и LWW:
  - Не везде строгая нормализация по `updatedAt` (источник правды при merge). Требуется стандартизировать
- Idempotency:
  - Для Outbox-операций не описаны идемпотентные ключи; риск двойной записи/дубликатов при ретраях
- Deletes (tombstones):
  - Политика удаления документов в Firestore и последующий merge в Room не документирована (soft delete vs hard delete)
- Индексы Firestore:
  - Для сложных whereEqualTo комбинаций (e.g., habit_entries с двумя полями) индексы должны быть проверены/зафиксированы
- Error handling:
  - Слушатели Firestore проглатывают ошибки (минимальные логи). Нужна централизованная обработка и метрики
- Battery/Data usage:
  - Множественные слушатели могут быть затратны. Требуются правила отписки/подписок по жизненному циклу экранов и скоупов

## 5) Предлагаемый план закрытия
1. Habits realtime + Outbox
   - Реализовать callbackFlow слушатели по `createdBy`/участникам
   - Outbox для create/update/delete Habit, унифицировать payload с полями `updatedAt`
2. Users + Google Sign-In
   - На первый signin/create/update юзера в Firestore и Room
   - FCM tokens: хранить массив, де-дуп, удалять старые токены
   - (Опц.) Реалтайм-слушатель профиля текущего user в Room
3. Challenges/Achievements
   - Добавить Outbox для create/update, и realtime-слушатели для коллекций
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
- `HabitRepositoryImpl`/соответствующий remote-адаптер: Outbox write + realtime listen
- `UserRepository`: Google Sign-In pathway + realtime (опционально)
- `ChallengeRepositoryImpl`, `AchievementRepository` аналоги: Outbox + realtime
- Централизовать `updatedAt` и idempotency ключи в `SyncManager`

Статус: документ обновляется по мере закрытия пунктов плана.
