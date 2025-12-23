# Unified Sync Architecture (Offline‑first Outbox + Realtime)

Цель: единая, предсказуемая и отказоустойчивая синхронизация данных между Room и Firestore.

## 1) Принципы
- **Room‑first writes**: Любая команда (create/update/delete) сперва апдейтит Room → UI мгновенно.
- **Outbox**: Параллельно кладём операцию в очередь и выполняем удалённо (Firestore) асинхронно; ретраи до успеха.
- **KickSync**: После enqueue в outbox репозиторий экспедитит синк: `scheduleOneTimeSync()` + неблокирующий `drainPending()` (чтобы правки не оставались только в Room).
- **Realtime listeners**: Firestore → merge в Room (upsert). UI всегда подписан на Room Flows.
- **Remote->Room guard**: Входящие данные из Firestore **не должны перетирать локальные изменения**. Базовое правило: если по сущности есть `pending/in_progress` в outbox — remote merge пропускаем.
- **LWW (точно)**: Для конфликтов используем `updatedAtMillis` (миллисекундная точность). Сравнение по `LocalDate` больше не считается надёжным для merge.
- **Soft-delete (tombstones)**: Для `habit` и `challenge` удаление доставляется через tombstone: `isActive=false`, `deletedAtMillis`, `updatedAtMillis` (документ не удаляется hard delete).

## 2) Outbox модель
- Таблица: `sync_outbox`
  - ключевые поля: `entityType`, `entityId`, `opType` (UPSERT|DELETE|ARRAY_UNION|ARRAY_REMOVE), `payloadJson`, `updatedAt`, `state` (PENDING|IN_PROGRESS|FAILED), `retryCount`, `lastError`.
- Контракт выполнения:
  - UPSERT: `set(doc, payload, merge=true)` (в `SyncManager.pushUpsert()`), дополнительно перед merge может быть попытка `update()` только отдельных флагов.
  - DELETE:
    - `habit`/`challenge`: `set({isActive=false, deletedAtMillis, updatedAtMillis}, merge=true)`
    - остальные: `delete()` (hard delete)
  - ARRAY_UNION/ARRAY_REMOVE: атомарные операции Firestore `FieldValue.arrayUnion/arrayRemove` (используется для `users.fcmTokens[]`).
- Политика ретраев: через `retryCount` и состояние outbox (UPSERT остаётся PENDING, DELETE имеет лимит попыток через enqueueSafeDelete).

## 3) Поле времени и конфликтология
- Для Outbox-операций используем `updatedAt` в outbox (millis) и кладём в payload `updatedAtMillis` для сущностей, где важна точность.
- Для Firestore -> Room merge применяем:
  - **pending-guard**: если есть pending/in_progress по `entityType/entityId`, remote не апсертим.
  - **LWW**: сравнение по `updatedAtMillis` (например, Habits/Challenges/UserAchievements).
  - **sticky merge**: для некоторых сущностей сохраняем локально-важные поля (пример: `user_achievements.isNotified`).

## 4) Idempotency
Текущая реализация не использует отдельный `opId` для идемпотентности на уровне Firestore-документа.

## 5) Realtime listeners
- Habits: `FirestoreRepositoryImpl.getUserHabits()` (Firestore callbackFlow) + merge в Room происходит в `HabitRepositoryImpl.startObservingRemoteHabits()`.
- FriendRequests/Friendships/HabitInvitations: `SocialRepositoryImpl` (Firestore listeners) + merge в Room c pending-guard.
- HabitEntries: `HabitEntryRepositoryImpl` (Firestore listeners) + merge в Room c pending-guard.
- User: `UserRepositoryImpl.observeUser()` (Firestore listener) + merge в Room c pending-guard.
- GroupHabitNotifications: `GroupHabitNotificationRepositoryImpl` (Firestore listener) + sticky merge read-state + pending-guard.
- Challenges: `ChallengeRepositoryImpl.observeChallengesForUser()` (Firestore listeners) + LWW по `updatedAtMillis`.
- Achievements: `AchievementRepositoryImpl` (Firestore listener на `user_achievements` -> guarded merge в Room + LWW по `updatedAtMillis`).

## 6) WorkManager
- Периодический воркер (15 мин) + одинарный при старте/успешной аутентификации.
- Воркер вызывает `SyncManager.drainPending()`.
- Для пользовательских действий репозитории используют **KickSync** (см. выше), чтобы не ждать периодического запуска.

## 7) Payload стандарты
- Общие поля: `id` (если применимо), `updatedAt` (формат зависит от сущности), плюс прикладные поля.
- Для сущностей:
  - User: `id, name, email, avatar, level, points, fcmToken(legacy)`
    - `fcmTokens[]` обновляется только через outbox ARRAY_UNION/ARRAY_REMOVE (мы не пишем массив через обычный UPSERT, чтобы не затирать multi-device токены).
  - Habit: `id, title, description, icon, type, frequency, participants[], inviteCode?, isActive, updatedAt(строка LocalDate), updatedAtMillis, deletedAtMillis?`
  - HabitInvitation: `id, habitId, fromUserId, toUserId, status, createdAt, updatedAt, deletedAt?`
  - HabitEntry: `habitId, userId, date, completed, completedAt?, value?, note?, createdAt, updatedAt, deletedAt?`
  - Challenge: `..., updatedAtMillis, deletedAtMillis?`
  - UserAchievement: `..., createdAtMillis, unlockedAtMillis?, updatedAtMillis`
  - Friendship/FriendRequest — аналогично.

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
1. Стандартизировать `updatedAtMillis` для остальных сущностей, где возможны конфликты.
2. Идемпотентность: добавить `opId` и/или `lastOpId` на уровне документа (по необходимости).
3. Soft-delete расширить на другие сущности, где требуется гарантированная доставка удаления.
4. Pending-guard для всех reconcile-веток.
