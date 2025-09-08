# План интеграции бэкенда (Symfony 7 + PostgreSQL + JWT) для HabitTracker

Документ для backend‑разработчика. 
Цель: заменить текущий Firebase/Firestore‑бэкенд на собственный stateless REST API, сохранив UX и существующую клиентскую логику (Room‑first + Outbox синхронизация, push‑уведомления, социальные функции, групповые привычки).

Последний апдейт: 2025‑09‑08

---

## 1. Текущая архитектура клиента (важно для совместимости)

- UI: Jetpack Compose (Material 3), MVVM.
- Clean Architecture: `presentation` → `domain` → `data`.
- Offline‑first и синхронизация:
  - Room‑first операции. UI подписан на Flows из Room.
  - Outbox (см. `app/src/main/java/com/habittracker/data/sync/SyncManager.kt`):
    - enqueueUpsert(entityType, entityId, payloadJson, updatedAt, opId)
    - enqueueDelete(...)
    - drainPending(): пушит в удалённое хранилище; LWW по `updatedAt`.
  - Воркеры: `SyncWorker` + `SyncWorkScheduler` (экспедированные OneTimeWork).
- Сейчас удалённое хранилище — Firestore; часть операций уже переведены на Outbox (habit, habit_entry, friend_request, friendship, habit_invitation). Некоторые чтения пока идут напрямую (например, поиск пользователей).
- Нотификации: FCM, клиент раньше мог дергать FCM напрямую. Для нового бэкенда лучше перенести отправку пушей на сервер (см. раздел 6).

Ключевые места в коде:
- Outbox: `SyncManager`, сериализаторы payload: `domain/util/SyncPayloads.kt`.
- Пример use case с Outbox: `MarkHabitCompletedUseCase`, `MarkHabitIncompleteUseCase`, `DeleteHabitUseCase`.
- Социалка: интерфейс `domain/repository/SocialRepository.kt`, реализация `data/repository/SocialRepositoryImpl.kt`.
- Firestore-реализация чтений/записей (для ориентира): `data/repository/FirestoreRepositoryImpl.kt`.

---

## 2. Канонические модели и поля (domain layer)

Ниже — контрактные поля, которые должны быть поддержаны на сервере/в БД. Все timestamp — epoch millis (Long). Даты для записей привычек — ISO `YYYY‑MM‑DD`.

### 2.1 User (`domain/model/User.kt`)
- id: String (UUID)
- name: String
- email: String
- avatarUrl: String? 
- avatarThumbnailUrl: String?
- level: Int = 1
- points: Int = 0
- streak: Int = 0
- longestStreak: Int = 0
- friendsCount: Int = 0
- fcmToken: String? (устаревшее поле; новое хранение — список токенов)
- createdAt: Long
- lastActiveAt: Long
- timezone: String (IANA)

Дополнительно (на сервере): список токенов FCM (таблица `user_fcm_tokens`), уникальность токена по всем пользователям.

### 2.2 Habit (`domain/model/Habit.kt`)
- id: String (UUID)
- title: String
- description: String
- icon: String (например, emoji)
- color: String (hex)
- type: enum HabitType = { PERSONAL, GROUP }
- frequency: enum HabitFrequency = { DAILY, WEEKLY, WEEKDAYS, WEEKENDS, CUSTOM }
- reminderTime: { hour: Int, minute: Int } | null
- reminderEnabled: Boolean (на клиенте выводится из fact наличия reminderTime)
- targetValue: Int (частота внутри дня)
- unit: String
- createdBy: String (userId)
- participants: [String] (userId)
- maxParticipants: Int (<= 10)
- inviteCode: String? (уникальный)
- isPublic: Boolean
- isActive: Boolean (soft delete: false)
- createdAt: Long
- updatedAt: Long

### 2.3 HabitEntry (`domain/model/HabitEntry.kt`) 
// Коротко: habit_entry — это запись о выполнении конкретной привычки конкретным пользователем в конкретную дату.
// habitId, userId, date (ISO YYYY-MM-DD) — составной ключ.
- habitId: String
- userId: String
- date: String (ISO LocalDate)
- completed: Boolean
- completedAt: Long? (если completed)
- value: Int = 1
- note: String? 
- createdAt: Long
- updatedAt: Long

Идентификатор записи в Outbox/Firestore: `${habitId}_${userId}_${date}`.
/*
Интеграция с бэкендом (вариант 2)

REST: PUT /habit-entries/{habitId}/{userId}/{date} (upsert), 
      GET /habit-entries?... — см. backend/openapi/openapi.yaml

БД: таблица habit_entries с PK (habit_id, user_id, date) — см. backend/sql/schema.sql
*/

### 2.4 Friendship/FriendRequest/HabitInvitation
- Friendship (`domain/model/Friendship.kt`)
  - userId1, userId2 (всегда отсортированы; пара — PK)
  - status: enum FriendshipStatus = { PENDING, ACCEPTED, BLOCKED, DECLINED }
  - requestedBy: String
  - requestedAt: Long
  - acceptedAt: Long?
  - blockedBy: String?
  - blockedAt: Long?
  - updatedAt: Long
- FriendRequest (`domain/model/FriendRequest.kt`)
  - id: String (UUID)
  - fromUserId: String
  - toUserId: String
  - status: enum FriendRequestStatus = { PENDING, ACCEPTED, DECLINED, EXPIRED }
  - message: String?
  - createdAt: Long
  - respondedAt: Long?
  - updatedAt: Long
- HabitInvitation (`domain/model/HabitInvitation.kt`)
  - id: String (UUID)
  - habitId: String
  - habitTitle: String (кэш)
  - fromUserId: String
  - fromUserName: String (кэш)
  - toUserId: String
  - message: String?
  - status: enum HabitInvitationStatus = { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED }
  - createdAt: Long
  - respondedAt: Long?
  - expiresAt: Long?

### 2.5 Gamification/Achievements (задел)
- Achievement, UserAchievement, UserStats — модели уже описаны в `domain/model/*`. 
На первом этапе можно не реализовывать логику, но таблицы и поля лучше спроектировать.

---

## 3. PostgreSQL: схема БД (DDL‑эскизы)

Ниже — рекомендуемая схема (имена, типы и индексы можно адаптировать). 
Все timestamp — `TIMESTAMPTZ`. Для LWW используем `updated_at` и constraint/транзакционную обработку на сервере.

```sql
-- users
CREATE TABLE users (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  email CITEXT UNIQUE NOT NULL,
  avatar_url TEXT,
  avatar_thumbnail_url TEXT,
  level INT NOT NULL DEFAULT 1,
  points INT NOT NULL DEFAULT 0,
  streak INT NOT NULL DEFAULT 0,
  longest_streak INT NOT NULL DEFAULT 0,
  friends_count INT NOT NULL DEFAULT 0,
  timezone TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  last_active_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

-- уникальные FCM токены (один токен не может принадлежать двум пользователям)
CREATE TABLE user_fcm_tokens (
  token TEXT PRIMARY KEY,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL
);

-- habits
CREATE TABLE habits (
  id UUID PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  icon TEXT NOT NULL,
  color TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('PERSONAL','GROUP')),
  frequency TEXT NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','WEEKDAYS','WEEKENDS','CUSTOM')),
  reminder_hour SMALLINT,
  reminder_minute SMALLINT,
  target_value INT NOT NULL DEFAULT 1,
  unit TEXT NOT NULL,
  created_by UUID REFERENCES users(id),
  max_participants INT NOT NULL DEFAULT 10,
  invite_code TEXT UNIQUE,
  is_public BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

-- участники групповой привычки
CREATE TABLE habit_participants (
  habit_id UUID REFERENCES habits(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (habit_id, user_id)
);
CREATE INDEX idx_habit_participants_user ON habit_participants(user_id);

-- записи выполнения привычек
CREATE TABLE habit_entries (
  habit_id UUID REFERENCES habits(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  date DATE NOT NULL,
  completed BOOLEAN NOT NULL,
  completed_at TIMESTAMPTZ,
  value INT NOT NULL DEFAULT 1,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (habit_id, user_id, date)
);
CREATE INDEX idx_habit_entries_user ON habit_entries(user_id);
CREATE INDEX idx_habit_entries_date ON habit_entries(date);

-- дружба
CREATE TABLE friendships (
  user_id1 UUID REFERENCES users(id) ON DELETE CASCADE,
  user_id2 UUID REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','BLOCKED','DECLINED')),
  requested_by UUID REFERENCES users(id),
  requested_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ,
  blocked_by UUID REFERENCES users(id),
  blocked_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id1, user_id2)
);
-- Требование упорядочивать пару (user_id1 < user_id2) на уровне сервиса.

-- запросы дружбы
CREATE TABLE friend_requests (
  id UUID PRIMARY KEY,
  from_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  to_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),
  message TEXT,
  created_at TIMESTAMPTZ NOT NULL,
  responded_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_friend_request_unique_pair ON friend_requests(from_user_id, to_user_id) WHERE status = 'PENDING';

-- приглашения в привычку
CREATE TABLE habit_invitations (
  id UUID PRIMARY KEY,
  habit_id UUID REFERENCES habits(id) ON DELETE CASCADE,
  habit_title TEXT NOT NULL,
  from_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  from_user_name TEXT NOT NULL,
  to_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  message TEXT,
  status TEXT NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','CANCELLED')),
  created_at TIMESTAMPTZ NOT NULL,
  responded_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_habit_invitations_to ON habit_invitations(to_user_id, status);
CREATE INDEX idx_habit_invitations_from ON habit_invitations(from_user_id, status);

-- (задел) достижения/челленджи — по моделям из domain/model/*
```

Примечания:
- Soft delete: для `habits` используем `is_active=false` + отложенное физическое удаление (cron) — аналог `enqueueSafeDelete`.
- LWW: при UPSERT сверять `updated_at` и отклонять старые обновления (HTTP 409/412 с пояснением).
- Для `friendships` — нормализуем пару (меньший UUID в `user_id1`).

---

## 4. JWT‑аутентификация и авторизация

- Stateless JWT (RS256 или HS256): `Authorization: Bearer <token>`.
- Источник идентичности:
  - Вариант A (рекомендуется): вход через Google Sign‑In. Клиент присылает Google ID Token → `POST /auth/google` → сервер валидирует по Google JWKS, создаёт/возвращает `users.id`, выдаёт собственный JWT.
  - Вариант B: классический логин/пароль (опционально, не требуется сейчас).
- Срок жизни JWT: 1–7 дней (на ваше усмотрение) + refresh‑механизм по необходимости.

---

## 5. REST API: контракты (под клиентскую логику)

Общие правила:
- Базовый URL: `/api/v1`.
- Заголовки: `Authorization: Bearer <jwt>`, `Content-Type: application/json`.
- Форматы времени: timestamp millis (Long), даты — `YYYY‑MM‑DD`.
- Idempotency: для мутаций поддерживать `X-Idempotency-Key` (UUID) — опционально, но желательно.
- LWW: поле `updatedAt` обязательно; сервер отклоняет более старые апдейты (409/412) или игнорирует.

### 5.1 Auth
- POST `/auth/google`
  - body: `{ "idToken": "<Google ID Token>" }`
  - 200: `{ accessToken, user: {...User} }`

### 5.2 Users
- GET `/users/me`
- GET `/users/{id}`
- GET `/users/search?q=...&limit=20`
- PUT `/users/me/fcm-tokens` — idempotent
  - body: `{ token: string }` (сервер удаляет этот токен у других пользователей и добавляет текущему)
- DELETE `/users/me/fcm-tokens` — body: `{ token }`

### 5.3 Friends & Friend Requests
- POST `/friend-requests`
  - body: `{ toUserId, message?, updatedAt }`
  - 200: `{ ...FriendRequest }`
- POST `/friend-requests/{id}/accept`
- POST `/friend-requests/{id}/decline`
- DELETE `/friend-requests/{id}` (cancel)
- GET `/friend-requests/incoming?userId=...`
- GET `/friend-requests/outgoing?userId=...`
- GET `/friends?userId=...` — список друзей (`Friendship` + данные пользователей)
- DELETE `/friends/{friendId}` — удалить из друзей
- POST `/friends/{userId}/block`
- POST `/friends/{userId}/unblock`

### 5.4 Habits
- POST `/habits` — создание
- GET `/habits?userId=...` — список активных привычек, где пользователь — создатель или участник
- GET `/habits/{id}`
- PUT `/habits/{id}` — полная замена
- PATCH `/habits/{id}` — частичное обновление (участники, isActive и т.д.)
- DELETE `/habits/{id}` — soft delete (`isActive=false`)
- POST `/habits/{id}/participants` — body: `{ userId }`
- DELETE `/habits/{id}/participants/{userId}`
- POST `/habits/{id}/generate-code` — генерирует/обновляет `inviteCode`
- POST `/habits/join`
  - body: `{ inviteCode }` → добавляет вызывающего пользователя в участники

### 5.5 Habit Entries
- PUT `/habit-entries/{habitId}/{userId}/{date}` — upsert одной записи
  - body: `{ completed, completedAt?, value, note?, updatedAt }`
- GET `/habit-entries?habitId=...&userId=...&from=YYYY-MM-DD&to=YYYY-MM-DD`
- GET `/habit-entries/{habitId}/{userId}/{date}`

### 5.6 Habit Invitations
- POST `/habit-invitations` — body: `{ habitId, toUserId, message?, updatedAt }`
- POST `/habit-invitations/{id}/accept`
- POST `/habit-invitations/{id}/decline`
- DELETE `/habit-invitations/{id}` (cancel)
- GET `/habit-invitations/incoming?userId=...`
- GET `/habit-invitations/outgoing?userId=...`
- GET `/habit-invitations?habitId=...`
- GET `/habit-invitations/count/pending?userId=...`

### 5.7 Notifications (FCM)
- POST `/notifications/push` (опционально сохранить совместимость с текущим клиентом)
  - body: `{ toUserId | fcmToken, title, body, data? }`
  - Рекомендуется перенести пуши на серверные триггеры (после успешного accept/decline/join/complete habit).

### 5.8 Batch Sync API (совместимость с Outbox)
Чтобы минимизировать изменения на клиенте можно (временно или постоянно) поддержать Outbox‑совместимые ручки:
- POST `/sync/upsert`
  - body: `{ entityType, entityId, payload, updatedAt, opId }`
- POST `/sync/delete`
  - body: `{ entityType, entityId, updatedAt, opId }`
Сервер распаковывает payload и применяет к соответствующим таблицам, соблюдая LWW и идемпотентность.

---

## 6. Бизнес‑правила и соответствие текущей логике

- LWW: все сущности имеют `updatedAt` (millis). Сервер принимает только апдейты с `updatedAt >= stored.updated_at`.
- Soft delete привычек: `isActive=false` + плановый hard delete (через cron/очередь), аналог `enqueueSafeDelete` (см. `DeleteHabitUseCase`).
- HabitEntry idempotency: уникальный PK `(habit_id, user_id, date)` гарантирует отсутствие дублей.
- Friendships: хранить пару упорядоченной (минимальный UUID — `user_id1`).
- FCM токены: токен может принадлежать только одному user — при регистрации токена удалять его у других.
- Инвайт‑коды: уникальные, короткие, конфигурируемая политика (TTL, maxJoin и т.п.).
- Частичная запись полей Habit (PATCH): сервер мержит только присланные поля (особенно важно для `participants`, `isActive`).

---

## 7. Индексация и производительность

- Индексы показаны в DDL (по часто фильтруемым полям: userId, status, date).
- Пагинация: `limit/offset` или keyset (по `created_at`, `id`).
- Кэширование: ETag/If‑None‑Match на некоторых GET (опц.).
- Rate limiting для публичных ручек (по IP/JWT subject).

---

## 8. Ошибки и ответы

- 400 — неверные данные/валидация.
- 401 — нет/просрочен JWT.
- 403 — нет прав.
- 404 — не найдено.
- 409/412 — конфликт LWW (`updatedAt` старше, чем в БД).
- 422 — доменные ограничения (например, лимит участников).

Единый формат ошибки:
```json
{ "error": { "code": "VALIDATION_ERROR", "message": "...", "details": {...} } }
```

---

## 9. Примеры payload (совместимо с текущим клиентом)

### 9.1 Sync Upsert habit_entry
```json
{
  "entityType": "habit_entry",
  "entityId": "<habitId>_<userId>_2025-09-08",
  "updatedAt": 1694150400000,
  "payload": {
    "habitId": "<habitId>",
    "userId": "<userId>",
    "date": "2025-09-08",
    "completed": true,
    "completedAt": 1694150500000,
    "value": 1,
    "note": "",
    "createdAt": 1694150400000,
    "updatedAt": 1694150400000
  },
  "opId": "<uuid>"
}
```

### 9.2 PATCH habit (soft delete)
```http
PATCH /api/v1/habits/{id}
{
  "isActive": false,
  "updatedAt": 1694150400000
}
```

### 9.3 Friend request accept
```http
POST /api/v1/friend-requests/{id}/accept
{
  "updatedAt": 1694150400000
}
```

---

## 10. План внедрения (рекомендуемый порядок)

1) Auth + Users + FCM tokens (миграция входа через Google, регистрация токенов).
2) Habits + HabitEntries (CRUD + upsert по ключу, soft delete, inviteCode, join).
3) Social: FriendRequests/Friendships, HabitInvitations.
4) Notifications: серверные триггеры пушей на ключевые события.
5) Batch Sync API (если хотим полную совместимость с клиентским Outbox без переписывания репозиториев).
6) (Позже) Challenges, Achievements — по моделям из `domain/model/*`.

---

## 11. Совместимость клиента и минимальные изменения Android‑клиента

- Вариант 1 (минимум изменений): реализовать на бэкенде `/sync/upsert` и `/sync/delete` → `SyncManager` будет слать операции как сейчас, только на ваш REST.
- Вариант 2 (более «чистый»): перевести репозитории на вызовы конкретных REST‑ручек (Users/Habits/Entries/...); Outbox оставить как механизм ретраев HTTP.
- Поиск пользователей: заменить Firestore range‑query на `GET /users/search?q=...`.
- Обновление FCM токенов: `PUT /users/me/fcm-tokens`.
- Реалтайм: опционально SSE/WebSocket; в противном случае — периодический sync/pull.

---

## 12. Migrations и housekeeping (сервер)

- Плановый hard delete для привычек с `is_active=false` (через N часов/дней).
- Очистка истёкших инвайтов (status=EXPIRED) и friend requests (EXPIRED).
- Уникальность/валидность `invite_code` (алфавит + длина 6–8, TTL, rate‑limit генерации).
- Поддержка `updated_at` триггерами или на уровне сервисного слоя.

---

## 13. Приложение: соответствие полей Firestore → PostgreSQL

- Habit.reminderTime {hour, minute} → `reminder_hour`, `reminder_minute`.
- Habit.isActive → `is_active` (soft delete).
- HabitEntry.date (ISO) → `DATE`.
- Массивы (`participants`, `fcmTokens`) → нормализованные таблицы связей.

---

