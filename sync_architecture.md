# Unified Sync Architecture (Offline‑first Outbox + Realtime)

Цель: единая, предсказуемая и отказоустойчивая синхронизация данных между Room и Firestore.

## 1) Принципы
- **Room‑first writes**: Любая команда (create/update/delete) сперва апдейтит Room → UI мгновенно.
- **Outbox**: Параллельно кладём операцию в очередь и выполняем удалённо (Firestore) асинхронно; ретраи до успеха.
- **Realtime listeners**: Firestore → merge в Room (upsert). UI всегда подписан на Room Flows.
- **Remote->Room guard**: Входящие данные из Firestore **не должны перетирать локальные изменения**. Базовое правило: если по сущности есть `pending/in_progress` в outbox — remote merge пропускаем.
- **LWW (ограниченно)**: LWW по `updatedAtMillis` используем только там, где это поле реально есть и имеет достаточную точность. Во многих доменных моделях `updatedAt` сейчас хранится как `LocalDate` и годится только для грубого сравнения.
- **Deletes (текущее поведение)**: Outbox DELETE делает **hard delete** в Firestore (без tombstones).

## 2) Outbox модель
- Таблица: `sync_outbox`
  - ключевые поля: `entityType`, `entityId`, `opType` (UPSERT|DELETE), `payloadJson`, `updatedAt`, `state` (PENDING|IN_PROGRESS|FAILED), `retryCount`, `lastError`.
- Контракт выполнения:
  - UPSERT: `set(doc, payload, merge=true)` (в `SyncManager.pushUpsert()`), дополнительно перед merge может быть попытка `update()` только отдельных флагов.
  - DELETE: `delete()` (hard delete).
- Политика ретраев: через `retryCount` и состояние outbox (UPSERT остаётся PENDING, DELETE имеет лимит попыток через enqueueSafeDelete).

## 3) Поле времени и конфликтология
- Для Outbox-операций используем `updatedAt` в outbox (millis). Однако в payload для части сущностей `updatedAt` хранится как строка `LocalDate`.
- Для Firestore -> Room merge применяем:
  - **pending-guard**: если есть pending/in_progress по `entityType/entityId`, remote не апсертим.
  - **LWW**: только там, где есть надёжное `updatedAtMillis` (например, у Challenge).
  - **sticky merge**: для некоторых сущностей нужно сохранять локально-важные поля (например, `notifications.isRead`).

## 4) Idempotency
Текущая реализация не использует отдельный `opId` для идемпотентности на уровне Firestore-документа.

## 5) Realtime listeners
- Habits: `FirestoreRepositoryImpl.getUserHabits()` (Firestore callbackFlow) + merge в Room происходит в `HabitRepositoryImpl.startObservingRemoteHabits()`.
- FriendRequests/Friendships/HabitInvitations: `SocialRepositoryImpl` (Firestore listeners) + merge в Room c pending-guard.
- HabitEntries: `HabitEntryRepositoryImpl` (Firestore listeners) + merge в Room c pending-guard.
- User: `UserRepositoryImpl.observeUser()` (Firestore listener) + merge в Room c pending-guard.
- GroupHabitNotifications: `GroupHabitNotificationRepositoryImpl` (Firestore listener) + sticky merge read-state + pending-guard.
- Challenges: `ChallengeRepositoryImpl.observeChallengesForUser()` (Firestore listeners) + LWW по `updatedAtMillis`.

## 6) WorkManager
- Периодический воркер (15 мин) + одинарный при старте/успешной аутентификации.
- Воркер вызывает `SyncManager.drainPending()` и (опц.) `reconcile()` — сверка критичных коллекций с Firestore (пагинация/дельты).

## 7) Payload стандарты
- Общие поля: `id` (если применимо), `updatedAt` (формат зависит от сущности), плюс прикладные поля.
- Для сущностей:
  - User: `id, name, email, avatar, level, points, fcmTokens[]`
  - Habit: `id, title, description, icon, type, frequency, participants[], inviteCode?, isActive, updatedAt(строка LocalDate)`
  - HabitInvitation: `id, habitId, fromUserId, toUserId, status, createdAt, updatedAt, deletedAt?`
  - HabitEntry: `habitId, userId, date, completed, completedAt?, value?, note?, createdAt, updatedAt, deletedAt?`
  - Friendship/FriendRequest/Challenge/Achievement — аналогично, с `updatedAt`/`deletedAt`.

## 8) API слоя
- Repository методы: Room-first; сразу `Dao.upsert()` + `SyncManager.enqueue(...)` с типом операции.
- `SyncManager`:
  - `enqueueUpsert(entityType, entityId, payloadJson, updatedAt, operationId=UUID)`
  - `enqueueDelete(entityType, entityId, updatedAt, operationId=UUID)`
  - `enqueueSafeDelete(entityType, entityId, notBeforeMillis, maxRetries, updatedAt, operationId=UUID)`
  - `hasPendingOperations(entityType, entityId): Boolean`
  - `drainPending(maxBatch)` — читает очередь, пушит в Firestore, меняет state/делает retry.

## 9) Безопасность и приватность
- Не логировать PII/секреты (например, полные FCM токены — маскировать).
- Валидация входных данных до помещения в Outbox.

## 10) Производительность
- Подписки на Realtime — по потребности экрана; отписываться при уходе со страницы.
- Использовать индексы Firestore для составных where.
- Минимизировать пересылку: компактные payload’ы; merge=true для UPSERT.

## 11) План улучшений (не реализовано полностью)
1. Стандартизировать `updatedAt` в Firestore как `updatedAtMillis` для всех сущностей, где важны конфликты.
2. Идемпотентность: добавить `opId` и/или `lastOpId` на уровне документа (по необходимости).
3. Soft-delete (tombstones) через `deletedAt` там, где критично корректно “доставлять удаления” на другие устройства.
4. Pending-guard для всех reconcile-веток (в т.ч. там, где сейчас есть удаление локальных записей по отсутствию в snapshot).
