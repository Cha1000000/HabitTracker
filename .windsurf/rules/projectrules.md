---
trigger: always_on
description: 
globs: 
---

# HabitTracker Project Rules

## Идея (цель проекта)
Разработать мобильное приложение типа трекер привычек, только что бы допустим несколько человек (друзей), которые будут пользоваться этим приложением могли создавать "общие" привычки и вместе за ними следить, устраивать по ним челенджи и т.п. 
Т.е. допустим создал я привычку «Зарядка по утрам» ну самое банальное. Я кидаю приглашение одному другу, второму, третьему и т.д. и вот мы втроем видим экран «привычки», и видим отметки каждого из нас. Кто пропустил, а кто отметил хотя бы.  Ну и так можно всякое мутить, + напоминания. Допустим решили мы с друзьями по четвергам вечером на турники выходить, создаем привычку и она нам напоминает. А мы сходили - отметили . Ну это базовая идея, А так можно еще развивать. Например какие то очивки придумать. Вплоть до того, что победивший получает там 500 руб. или какую-нибудь награду. Короче надо чтоб удобно и весело можно было.  Ну и чтоб в solo режиме тоже работало нормально. 
Идея в том, что бы сделать просто но функционально, и чтоб можно было кооперативить с друзьями, типа челленджей.

## Архитектура проекта
- Используй Clean Architecture (Presentation -> Domain -> Data)
- MVVM паттерн с Compose UI
- Repository pattern для работы с данными
- Use Cases для бизнес-логики
- Dependency Injection с Hilt

## Kotlin специфика для HabitTracker
- data classes для всех моделей (User, Habit, HabitEntry, Challenge, etc.)
- sealed classes для UI состояний и результатов операций
- Coroutines + StateFlow для reactive programming
- Extension functions для улучшения читаемости дат и времени
- Nullable safety везде, особенно при работе с сетью

## Android компоненты
- Jetpack Compose для всего UI
- Navigation Compose для навигации между экранами
- ViewBinding НЕ используем (только Compose)
- Material 3 Design System
- WorkManager для фоновых напоминаний
- Не хардкодить текстовые компоненты UI - выносить тексты в strings.xml (отдельно для каждой локализации)

## Структура пакетов
```
com.habittracker/
├── presentation/
│   ├── ui/
│   │   ├── habits/
│   │   ├── friends/
│   │   ├── challenges/
│   │   ├── profile/
│   │   ├── auth/
│   │   └── theme/
│   ├── viewmodel/
│   ├── navigation/
│   └── state/
├── domain/
│   ├── model/
│   │   └── gamification/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── local/ (Room, DataStore)
│   ├── remote/ (Firestore)
│   ├── repository/ (Implementations)
│   ├── worker/ (WorkManager)
│   ├── analytics/
│   └── sync/
└── di/
```

## Модели данных
- User: id, name, email, avatarUrl, level, points, streak, longestStreak, friendsCount, fcmToken, lastActiveAt, timezone, createdAt
- Habit: id, title, description, icon, color, type(personal/group), frequency, reminderTime, reminderEnabled, targetValue, unit, createdBy, participants, inviteCode, startDate, endDate, followUpReminderEnabled, goalType(simple/measurable)
- HabitEntry: habitId, userId, date, completed, value, note, isRestDay, streakBonusAwardedDays, completedAt
- Challenge: id, habitId, title, description, createdBy, participants, acceptedParticipants, startDate, endDate, rules, reward, status, winnerId
- Friendship: userId1, userId2, status, requestedBy, requestedAt, acceptedAt, blockedBy


## Особенности UI для социального трекера
- Показывай статус друзей в реальном времени
- Используй зеленые/красные индикаторы для выполненных/пропущенных дней
- Карточки привычек должны показывать участников с аватарками
- Анимации для отметки выполнения (confetti, checkmarks)
- Прогресс-бары для групповых челленджей (используй GradientProgressBar из TahoeComponents.kt)
- Проектируй UI как крутой, опытный UI/UX дизайнер и продумывай интерфейс так, чтоб даже не опытному пользователю было всё интуитивно понятно (не делай сложный и запутанный интерфейс)
- Следуй лучшим практикам разработки UI/UX современных мобильных приложений
- Следи за коррктным выбором цветов текстов, иконок и других компонентов интерфейса с учётом тёмной и светлой темы, для обеспечения лучшего зрительного восприятия и удобочитаемости

## База данных и синхронизация
- Room для локального кэша с офлайн-поддержкой
- Firebase Firestore для синхронизации между устройствами
- Conflict resolution стратегия: last-write-wins для большинства операций
- Оптимистичные апдейты в UI

## Уведомления и напоминания
- WorkManager для локальных напоминаний
- Firebase Cloud Messaging для push-уведомлений о друзьях
- Умные напоминания: учитывай часовые пояса участников группы
- Навигация из уведомлений: по нажатии на push-уведомление пользователь дольжен попать на соответствующий экран (относящийся по логие и контексту этого уведомления)

## Тестирование
- Unit тесты для ViewModels и Use Cases обязательны
- Repository тесты с Mock данными
- UI тесты для критических flow (создание привычки, отметка выполнения)
- Тестируй edge cases: нет интернета, медленная сеть

## Геймификация
- Система очков: 
  - +10 за выполнение привычки
  - +50 за выполнение всех привычек за день (Perfect Day)
  - +25 за создание групповой привычки
  - +50 за нового друга
  - +100 за победу в челлендже
- Бонусы за серии (Streak): 
  - 3 дня: +20 очков
  - 7 дней: +50 очков
  - 30 дней: +200 очков
- Уровни пользователей (1000 очков на уровень):
  - 1-5 ур: Новичок
  - 6-15 ур: Энтузиаст
  - 16-30 ур: Эксперт
  - 31-50 ур: Мастер
  - 51+ ур: Легенда
- Достижения и рейтинги с еженедельным обновлением


## Производительность
- LazyColumn для списков привычек
- Coil для загрузки аватарок с кэшированием
- Pagination для истории выполнений
- Минимизируй recomposition в Compose

## Безопасность и приватность
- Валидация всех входных данных
- Шифрование чувствительных данных локально
- Не храни пароли в plain text
- GDPR compliance для пользовательских данных
## Role Routing

Для автоматического выбора роли и skills при обработке запроса используй правила из:
`/Users/racerkafa/Documents/MyProjects/Android/Compose/HabitTracker/.agents/roles/Roles.md`

Требования:
1. Сначала определить роль(и) по интенту задачи.
2. Затем выбрать соответствующие skills по `Roles.md`.
3. При явном указании роли/skills пользователем — это приоритетнее авто-роутинга.
