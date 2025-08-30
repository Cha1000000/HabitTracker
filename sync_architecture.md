# Unified Sync Architecture (Offline‑first Outbox + Realtime)

Цель: единая, предсказуемая и отказоустойчивая синхронизация данных между Room и Firestore.

## 1) Принципы
- **Room‑first writes**: Любая команда (create/update/delete) сперва апдейтит Room → UI мгновенно.
- **Outbox**: Параллельно кладём операцию в очередь и выполняем удалённо (Firestore) асинхронно; ретраи до успеха.
- **Realtime listeners**: Firestore → merge в Room (upsert/soft delete). UI всегда подписан на Room Flows.
- **LWW (last‑write‑wins)**: Поле `updatedAt` (millis) — источник истины при конфликте.
- **Idempotency**: Каждая операция имеет `opId` (UUID). Повторная обработка не приводит к дубликатам.
- **Deletes**: Soft‑delete через `deletedAt` (millis) вместо жёсткого удаления — для корректного мерджа и оффлайн сценариев.

## 2) Outbox модель
- Таблица: `sync_outbox`
  - `id` (PK), `opId` (unique), `collection`, `docId`, `type` (UPSERT|DELETE|ARRAY_UNION|ARRAY_REMOVE), `payload` (JSON), `updatedAt`, `retries`, `lastError`.
- Контракт выполнения:
  - UPSERT: `set(doc, payload, merge=true)`; Firestore doc обязан содержать `updatedAt`.
  - DELETE: если soft — `update { deletedAt=now, updatedAt=now }`; если hard — `delete()` (редко).
  - ARRAY_UNION/REMOVE: atomic updates для массивов (например, participants/FCM tokens).
- Политика ретраев: экспоненциальный/линейный backoff via WorkManager; лимит и логирование.

## 3) Поле времени и конфликтология
- `updatedAt` всегда генерируется на клиенте (Clock.System.now()).
- При приходе снапшота Firestore → перед upsert в Room сравнить `remote.updatedAt` с `local.updatedAt`.
  - Если remote >= local → применяем remote.
  - Иначе игнорируем remote (локальные изменения новее).
- Для массивов с atomic updates — опираемся на сам Firestore как на source-of-truth; в Room храним результат.

## 4) Idempotency
- Каждый Outbox item получает `opId` (UUID).
- На Firestore уровне (если уместно) сохраняем `lastOpId` в документе.
- Перед применением операции проверять, не применялась ли уже (`lastOpId == opId`). Для простоты можно хранить `processedOpIds` в doc (ограничивать размер) или полагаться на семантику set(merge) при UPSERT.

## 5) Realtime listeners
- Для каждой коллекции, используемой в UI, создаём `callbackFlow` listener + merge в Room:
  - Users (текущий пользователь, профиль друзей — опционально)
  - Habits (по участию: createdBy == me или participants содержит me)
  - FriendRequests, Friendships (реализовано)
  - HabitInvitations (реализовано)
  - HabitEntries (реализовано)
  - Challenges, Achievements (план)
- Ошибки слушателей логировать безопасно; не падать.

## 6) WorkManager
- Периодический воркер (15 мин) + одинарный при старте/успешной аутентификации.
- Воркер вызывает `SyncManager.drainPending()` и (опц.) `reconcile()` — сверка критичных коллекций с Firestore (пагинация/дельты).

## 7) Payload стандарты
- Общие поля: `id`, `updatedAt`, `deletedAt?` (nullable), `opId?` (при необходимости).
- Для сущностей:
  - User: `id, name, email, avatar, level, points, fcmTokens[]`
  - Habit: `id, title, description, icon, type, frequency, participants[], inviteCode?, updatedAt, deletedAt?`
  - HabitInvitation: `id, habitId, fromUserId, toUserId, status, createdAt, updatedAt, deletedAt?`
  - HabitEntry: `habitId, userId, date, completed, completedAt?, value?, note?, createdAt, updatedAt, deletedAt?`
  - Friendship/FriendRequest/Challenge/Achievement — аналогично, с `updatedAt`/`deletedAt`.

## 8) API слоя
- Repository методы: Room-first; сразу `Dao.upsert()` + `SyncManager.enqueue(...)` с типом операции.
- `SyncManager`:
  - `enqueue(collection, docId, type, payload, opId?)`
  - `drainPending()` — читает очередь, пушит в Firestore, отмечает успех, увеличивает `retries` на ошибке.
  - (опц.) `reconcile()` — для критичных коллекций: сравнивает `updatedAt` и подтягивает недостающие документы.

## 9) Безопасность и приватность
- Не логировать PII/секреты (например, полные FCM токены — маскировать).
- Валидация входных данных до помещения в Outbox.

## 10) Производительность
- Подписки на Realtime — по потребности экрана; отписываться при уходе со страницы.
- Использовать индексы Firestore для составных where.
- Минимизировать пересылку: компактные payload’ы; merge=true для UPSERT.

## 11) Переходный план внедрения
1. Users + Google Sign-In: Room-first create/update + Outbox UPSERT, ARRAY_UNION для `fcmTokens`; listener профиля.
2. Habits: Outbox UPSERT/DELETE + listeners по участию.
3. Challenges/Achievements: Outbox + listeners.
4. Внедрить LWW/Idempotency в `SyncManager` (если частично отсутствуют).
5. Добавить `deletedAt` и мягкие удаления по всем коллекциям, обновить мапперы Room.
6. Метрики/логи для drain и ошибок слушателей.
