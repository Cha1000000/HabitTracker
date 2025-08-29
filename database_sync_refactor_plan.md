Ниже — оптимальный, надёжный порядок внедрения, как сделал бы старший Android-инженер. Я учитываю критичную проблему с друзьями и цель — быстро вернуть консистентность, не жертвуя долгосрочной архитектурой.

Порядок работ
Аудит + фиксация текущих разрывов
Почему сейчас: минимальная стоимость, задаёт рамки.
Что делаем: инвентаризация сущностей (Room vs Firestore), список слушателей, write-пути, наличие updatedAt/идемпотентности.
Артефакт: документ с таблицей путей и gap-ами.
Связано:
SocialRepositoryImpl.kt
(особенно
getFriends()
),
MainActivity.kt
.
Срочный “read-only” фикс консистентности: Firestore→Room listeners для друзей
Почему сейчас: быстро устраняет расхождения без риска на write-пути.
Что делаем: добавить callbackFlow/snapshot listeners для friendships и friend_requests с upsert в Room, по аналогии с
getIncomingHabitInvitations(...)
в
SocialRepositoryImpl
.
Артефакт: актуальные друзья и запросы дружбы на всех устройствах.
Ссылки:
SocialRepositoryImpl.getFriends
читает только Room — добавляем источник обновления Room через Firestore.
База для надёжной двунаправленной синхронизации: Outbox + SyncManager + миграция
Почему сейчас: фундамент offline-first и ретраев.
Что делаем:
SyncOutboxEntity/SyncOutboxDao, миграция Room (версия БД + скрипт).
SyncManager с enqueue(op), drainOutbox(), LWW по updatedAt.
Идемпотентность: UUID операции в payload.
Артефакт: гарантированный канал Room→Firestore.
Перевод write-путей Social на Room-first + Outbox
Почему сейчас: замыкаем цикл для social.
Что делаем: в
SocialRepositoryImpl
все write-операции: сначала upsert в Room (с updatedAt, syncStatus=PENDING), затем enqueue в Outbox.
Артефакт: стабильная запись с офлайн-очередью.
WorkManager: OneTime + Periodic (15 мин, сеть треб.)
Почему сейчас: выкачивает Outbox, поддерживает фоновые ретраи.
Что делаем: SyncWorker (drain + reconcile), PeriodicWorkRequest минимум 15 минут, Constraints = network required, OneTimeWork при старте/логине.
Артефакт: регулярный синк без участия UI.
Стартап-синк после авторизации + лёгкий UI индикации
Почему сейчас: UX-плавность и быстрый “catch-up” локального кэша.
Что делаем: в
MainActivity
после AuthState.Success запуск OneTime синка, небольшой индикатор/логирование.
Ссылка:
MainActivity.kt
(у вас на 43-й строке контекст авторизации).
Fix Google Sign-In: создание/апдейт User + FCM tokens
Почему сейчас: база пользователя — ключ ко всем слушателям и sync.
Что делаем:
Проверка/создание users/{uid} в Firestore (name, email, avatar, createdAt, lastActiveAt, fcmTokens=arrayUnion(token), updatedAt).
Upsert в Room User.
Всё через Room-first + Outbox, где уместно.
Артефакт: корректная первичная инициализация.
Расширение слушателей и Room-first на Habits/HabitEntries
Почему сейчас: закрываем остальные сущности после стабилизации social.
Что делаем: те же паттерны (listeners + Outbox) для habits, habit_entries.
Тесты и миграции
Почему сейчас: завершаем гарантией качества.
Что делаем: юнит-тесты SyncManager (успех/ретраи/идемпотентность), контрактные тесты репозиториев, тесты офлайна, валидация миграций Room.
Телеметрия/логирование и фичефлаг
Почему сейчас: поддерживаем эксплуатацию и быстрый rollback при необходимости.
Что делаем: структурные логи sync-операций, флаг для отключения real-time sync.
Критические детали
Минимальный интервал PeriodicWork — 15 минут (не 5).
Конфликты — LWW на updatedAt (epoch millis).
Чтение в UI — строго из Room.
Вставки в Room — bulk upsert для снижения recomposition.
Безопасность — валидируем входные данные, не логируем чувствительное, не хардкодим ключи.
Рекомендация по старту
Начать с шага 2 (listeners для друзей/запросов), чтобы сразу снять главную боль с консистентностью.
Сразу после — шаг 3 (Outbox + SyncManager) и шаг 4 (Room-first в social write-путях) — это зацементирует надёжный цикл.