# План миграции данных: Firestore → PostgreSQL (Symfony 7)

Цель: перенести пользователей, привычки, записи выполнения, дружбу и приглашения из Firestore в PostgreSQL, не ломая клиент (Room-first + Outbox) и обеспечив LWW совместимость.

## 1) Подготовка
- Развернуть PostgreSQL и применить схемы:
  - `backend/sql/schema.sql`
  - `backend/sql/auth_extras.sql`
- Поднять API (Symfony 7) с JWT.
- Включить временные read-only слушатели Firestore для сверки данных (reconcile) на этапе валидации.

## 2) Экспорт данных из Firestore
- Экспорт коллекций в JSON/NDJSON (или напрямую через Admin SDK):
  - `users`
  - `habits`
  - `habit_entries`
  - `friend_requests`
  - `friendships`
  - `habit_invitations`
- Для больших коллекций: батчи по 5–10 тыс. документов; сохранять `updatedAt`/`deletedAt/isActive`.

## 3) Трансформация данных
- Приведение полей к целевым:
  - Timestamp → millis → `TIMESTAMPTZ` (UTC) в БД.
  - `Habit.reminderTime{hour,minute}` → `reminder_hour`,`reminder_minute`.
  - `Habit.isActive` → `is_active`.
  - `HabitEntry.date` (ISO) → `DATE`.
- Нормализация массивов:
  - `participants[]` → `habit_participants`.
  - `fcmTokens[]` → `user_fcm_tokens` (удалить дубликаты токенов и токены, которые принадлежат нескольким юзерам).
- Очистка/валидация:
  - Обязательные поля (id, updatedAt и т.д.).
  - Удалить мягко удалённые (isActive=false) — по политике: либо переносим как is_active=false, либо пропускаем.

## 4) Загрузка в Postgres (ETL)
- Порядок вставки (FK-целостность):
  1) `users`
  2) `habits`
  3) `habit_participants`
  4) `habit_entries`
  5) `friendships`
  6) `friend_requests`
  7) `habit_invitations`
- Вставка батчами по 1000–5000 строк, транзакциями. Логировать ошибки.
- При коллизиях PK — проверять `updated_at` и выбирать запись с большим значением (LWW).

## 5) Верификация и сверка
- Сравнить количество записей по таблицам.
- Случайная выборка документов: поля и `updated_at` должны совпадать с источником.
- Индексы: убедиться, что запросы по userId/date/status работают быстро.

## 6) Переключение клиента
- Этап 1: режим «двойной записи» (опционально):
  - Клиент Outbox пишет в REST, а Firestore listeners остаются для сравнения.
- Этап 2: отключить Firestore listeners и старые записи; включить REST/БД как единственный source.
- Обновить Android:
  - Поиск пользователей → `GET /users/search`.
  - Вход/refresh/logout — через новые ручки `/auth/*`.
  - Все записи habit/habit_entries/friend/* — через репозитории REST (или Outbox → REST в бэкграунде).

## 7) Откат и резервирование
- Резервные дампы БД перед импортом и после импорта.
- Логи ETL и инкрементальные повторные прогоны для «хвостов».

## 8) Пост-миграционный контроль
- Метрики API: время ответов, 4xx/5xx, RPS.
- Ошибки синхронизации Outbox (коды 409/412 при LWW) — мониторить.
- Чистка `is_active=false` привычек по крону.

## 9) Инструменты
- Скрипты экспорта/трансформации: Python/NodeJS (на ваш выбор).
- Для больших объёмов — COPY из CSV.

## 10) Сроки и этапность
- S0: подготовка схем и серверов — 0.5–1 день.
- S1: экспорт Firestore — 0.5–1 день.
- S2: трансформация/загрузка — 1–2 дня.
- S3: верификация/переключение — 0.5–1 день.

Результат: сервер на Symfony с Postgres становится единой точкой, клиент продолжает работать offline-first через Outbox + REST.
