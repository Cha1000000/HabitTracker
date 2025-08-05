# 🚀 План реализации социальных функций HabitTracker

## 📋 Общий обзор

Этот документ содержит подробный поэтапный план реализации социальных функций для HabitTracker, включая систему друзей, групповые привычки, челленджи и геймификацию.

### 🎯 Ключевые требования:
- **Приглашения**: Push-уведомления через Firebase Cloud Messaging
- **Лимиты**: До 10 участников в групповой привычке
- **Челленджи**: Только между друзьями
- **Архитектура**: Только Firebase Firestore + FCM (без отдельного бэкенда)
- **Статистика**: Рейтинги, стрики, достижения

---

## 🏗️ ЭТАП 1: Система друзей (2-3 дня)

### 📊 Цель этапа
Реализовать полноценную систему друзей с поиском, отправкой запросов, принятием/отклонением заявок.

### 🔧 Контрольная точка 1.1: Модели данных (0.5 дня)
**Задачи:**
- [ ] Расширить модель `User` с социальными полями
- [ ] Обновить модель `Friendship` 
- [ ] Создать модель `FriendRequest`
- [ ] Обновить Firestore коллекции

**Файлы для создания/изменения:**
```
domain/model/User.kt (обновить)
domain/model/Friendship.kt (обновить)
domain/model/FriendRequest.kt (создать)
```

**Структура данных Firestore:**
```kotlin
// Коллекция users
{
  "id": "user123",
  "name": "John Doe",
  "email": "john@example.com",
  "avatar": "url",
  "level": 1,
  "points": 100,
  "friendsCount": 5,
  "createdAt": timestamp,
  "fcmToken": "token" // для push-уведомлений
}

// Коллекция friendships
{
  "id": "friendship123",
  "userId1": "user123",
  "userId2": "user456",
  "status": "accepted", // pending, accepted, blocked
  "createdAt": timestamp,
  "acceptedAt": timestamp?
}

// Коллекция friend_requests
{
  "id": "request123",
  "fromUserId": "user123",
  "toUserId": "user456",
  "status": "pending", // pending, accepted, declined
  "message": "Давай дружить!",
  "createdAt": timestamp
}
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Модели созданы и соответствуют архитектуре
- ✅ Firestore коллекции спроектированы

---

### 🔧 Контрольная точка 1.2: Repository и Use Cases (1 день)
**Задачи:**
- [ ] Создать `SocialRepository` интерфейс
- [ ] Реализовать `SocialRepositoryImpl` с Firestore
- [ ] Создать Use Cases для друзей
- [ ] Обновить Hilt модули

**Файлы для создания:**
```
domain/repository/SocialRepository.kt
data/repository/SocialRepositoryImpl.kt
domain/usecase/social/SearchUsersUseCase.kt
domain/usecase/social/SendFriendRequestUseCase.kt
domain/usecase/social/AcceptFriendRequestUseCase.kt
domain/usecase/social/DeclineFriendRequestUseCase.kt
domain/usecase/social/GetFriendsUseCase.kt
domain/usecase/social/GetFriendRequestsUseCase.kt
domain/usecase/social/RemoveFriendUseCase.kt
di/SocialModule.kt
```

**Use Cases детали:**
```kotlin
// SearchUsersUseCase
- Поиск по имени/email
- Исключение уже добавленных друзей
- Пагинация результатов

// SendFriendRequestUseCase  
- Проверка на существующие запросы
- Отправка FCM уведомления
- Сохранение в Firestore

// AcceptFriendRequestUseCase
- Создание записи friendship
- Удаление friend_request
- Обновление счетчиков друзей
- FCM уведомление отправителю
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Все Use Cases покрыты unit-тестами
- ✅ Repository интегрирован с Firestore
- ✅ Hilt DI настроен

---

### 🔧 Контрольная точка 1.3: UI экраны друзей (1 день)
**Задачи:**
- [ ] Создать экран поиска друзей
- [ ] Создать экран списка друзей
- [ ] Создать экран запросов в друзья
- [ ] Обновить навигацию
- [ ] Добавить строковые ресурсы

**Файлы для создания:**
```
presentation/ui/social/FriendsScreen.kt
presentation/ui/social/SearchFriendsScreen.kt
presentation/ui/social/FriendRequestsScreen.kt
presentation/viewmodel/SocialViewModel.kt
presentation/navigation/SocialNavigation.kt
```

**UI компоненты:**
```kotlin
// FriendsScreen
- LazyColumn со списком друзей
- Поиск по имени
- Swipe-to-remove действие
- FAB для добавления друзей

// SearchFriendsScreen
- SearchBar с живым поиском
- LazyColumn результатов
- Кнопки "Добавить в друзья"
- Состояния загрузки/ошибки

// FriendRequestsScreen
- Входящие запросы
- Исходящие запросы
- Кнопки принять/отклонить
- Badges с количеством
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Все экраны работают с mock данными
- ✅ Навигация между экранами настроена
- ✅ UI соответствует Material 3

---

### 🔧 Контрольная точка 1.4: FCM интеграция (0.5 дня)
**Задачи:**
- [ ] Настроить Firebase Cloud Messaging
- [ ] Создать сервис для обработки уведомлений
- [ ] Интегрировать с Use Cases
- [ ] Тестирование уведомлений

**Файлы для создания/изменения:**
```
data/fcm/HabitTrackerMessagingService.kt
data/fcm/NotificationHelper.kt (обновить)
AndroidManifest.xml (обновить)
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ FCM токены сохраняются в Firestore
- ✅ Push-уведомления работают
- ✅ Система друзей полностью функциональна

---

## 🏗️ ЭТАП 2: Групповые привычки (2-3 дня)

### 📊 Цель этапа
Добавить возможность создавать групповые привычки с приглашением друзей и отслеживанием совместного прогресса.

### 🔧 Контрольная точка 2.1: Обновление моделей привычек (0.5 дня)
**Задачи:**
- [ ] Обновить модель `Habit` для групповых привычек
- [ ] Создать модель `HabitInvitation`
- [ ] Обновить `HabitEntry` для группового контекста

**Обновления моделей:**
```kotlin
// Habit (обновить)
data class Habit(
    // ... существующие поля
    val isGroup: Boolean = false,
    val participants: List<String> = emptyList(), // user IDs
    val maxParticipants: Int = 10,
    val createdBy: String, // user ID создателя
    val inviteCode: String? = null // для приглашений
)

// HabitInvitation (создать)
data class HabitInvitation(
    val id: String,
    val habitId: String,
    val fromUserId: String,
    val toUserId: String,
    val status: InvitationStatus,
    val message: String?,
    val createdAt: Long
)

enum class InvitationStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED
}
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Миграция данных не требуется (обратная совместимость)
- ✅ Firestore схема обновлена

---

### 🔧 Контрольная точка 2.2: Use Cases для групповых привычек (1 день)
**Задачи:**
- [ ] Обновить существующие Use Cases привычек
- [ ] Создать Use Cases для приглашений
- [ ] Создать Use Cases для группового прогресса

**Файлы для создания/обновления:**
```
domain/usecase/habit/CreateGroupHabitUseCase.kt
domain/usecase/habit/InviteToHabitUseCase.kt
domain/usecase/habit/AcceptHabitInvitationUseCase.kt
domain/usecase/habit/DeclineHabitInvitationUseCase.kt
domain/usecase/habit/LeaveGroupHabitUseCase.kt
domain/usecase/habit/GetGroupProgressUseCase.kt
domain/usecase/habit/GetHabitInvitationsUseCase.kt
```

**Ключевые Use Cases:**
```kotlin
// InviteToHabitUseCase
- Проверка лимита участников (10)
- Проверка дружбы с приглашаемым
- Отправка FCM уведомления
- Создание записи приглашения

// GetGroupProgressUseCase
- Агрегация прогресса всех участников
- Расчет групповой статистики
- Определение лидеров группы
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Use Cases покрыты тестами
- ✅ Интеграция с существующими Use Cases

---

### 🔧 Контрольная точка 2.3: UI для групповых привычек (1.5 дня)
**Задачи:**
- [ ] Обновить AddEditHabitScreen для групповых привычек
- [ ] Создать экран выбора участников
- [ ] Обновить HabitsScreen с групповым прогрессом
- [ ] Создать экран приглашений в привычки

**Файлы для обновления/создания:**
```
presentation/ui/habits/AddEditHabitScreen.kt (обновить)
presentation/ui/habits/SelectParticipantsScreen.kt (создать)
presentation/ui/habits/HabitInvitationsScreen.kt (создать)
presentation/ui/habits/GroupProgressScreen.kt (создать)
presentation/viewmodel/AddEditHabitViewModel.kt (обновить)
```

**UI обновления:**
```kotlin
// AddEditHabitScreen
- Переключатель "Групповая привычка"
- Кнопка "Добавить участников" (только для друзей)
- Список выбранных участников
- Лимит 10 участников

// GroupProgressScreen
- Карточки участников с прогрессом
- Общая статистика группы
- Рейтинг участников
- Кнопка "Пригласить еще"
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ UI работает с mock данными
- ✅ Групповые привычки создаются и отображаются
- ✅ Приглашения работают

---

## 🏗️ ЭТАП 3: Челленджи между друзьями (2 дня)

### 📊 Цель этапа
Реализовать систему челленджей между друзьями с соревновательным элементом.

### 🔧 Контрольная точка 3.1: Модели челленджей (0.5 дня)
**Задачи:**
- [ ] Создать модель `Challenge`
- [ ] Создать модель `ChallengeParticipant`
- [ ] Определить типы челленджей

**Модели данных:**
```kotlin
data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val habitId: String,
    val createdBy: String,
    val participants: List<String>,
    val startDate: Long,
    val endDate: Long,
    val rules: ChallengeRules,
    val reward: String?, // символическая награда
    val status: ChallengeStatus,
    val createdAt: Long
)

data class ChallengeRules(
    val type: ChallengeType,
    val targetValue: Int,
    val unit: String
)

enum class ChallengeType {
    STREAK, // кто дольше не пропустит
    TOTAL_COUNT, // кто больше выполнит
    FIRST_TO_TARGET // кто первый достигнет цели
}

enum class ChallengeStatus {
    UPCOMING, ACTIVE, COMPLETED, CANCELLED
}
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Firestore коллекции спроектированы
- ✅ Модели соответствуют архитектуре

---

### 🔧 Контрольная точка 3.2: Use Cases челленджей (1 день)
**Задачи:**
- [ ] Создать Use Cases для управления челленджами
- [ ] Реализовать логику подсчета результатов
- [ ] Интегрировать с системой уведомлений

**Файлы для создания:**
```
domain/usecase/challenge/CreateChallengeUseCase.kt
domain/usecase/challenge/JoinChallengeUseCase.kt
domain/usecase/challenge/LeaveChallengeUseCase.kt
domain/usecase/challenge/GetActiveChallengesUseCase.kt
domain/usecase/challenge/GetChallengeResultsUseCase.kt
domain/usecase/challenge/UpdateChallengeProgressUseCase.kt
```

**Ключевая логика:**
```kotlin
// CreateChallengeUseCase
- Проверка дружбы с участниками
- Валидация дат и правил
- Отправка приглашений

// UpdateChallengeProgressUseCase
- Автоматическое обновление при выполнении привычки
- Расчет текущих позиций
- Определение победителей
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Use Cases покрыты тестами
- ✅ Логика челленджей работает

---

### 🔧 Контрольная точка 3.3: UI челленджей (0.5 дня)
**Задачи:**
- [ ] Создать экран списка челленджей
- [ ] Создать экран создания челленджа
- [ ] Создать экран результатов челленджа
- [ ] Интегрировать с навигацией

**Файлы для создания:**
```
presentation/ui/challenges/ChallengesScreen.kt
presentation/ui/challenges/CreateChallengeScreen.kt
presentation/ui/challenges/ChallengeDetailsScreen.kt
presentation/viewmodel/ChallengesViewModel.kt
```

**UI компоненты:**
```kotlin
// ChallengesScreen
- Активные челленджи
- Завершенные челленджи
- FAB создания челленджа
- Фильтры по статусу

// ChallengeDetailsScreen
- Информация о челлендже
- Таблица лидеров
- Прогресс участников
- Кнопки действий
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Челленджи создаются и отображаются
- ✅ Результаты подсчитываются корректно

---

## 🏗️ ЭТАП 4: Геймификация (1-2 дня)

### 📊 Цель этапа
Добавить систему очков, уровней, достижений и социальной статистики.

### 🔧 Контрольная точка 4.1: Система очков и уровней (1 день)
**Задачи:**
- [ ] Реализовать систему начисления очков
- [ ] Создать систему уровней
- [ ] Обновить модель User
- [ ] Интегрировать с существующими Use Cases

**Система очков:**
```kotlin
// Правила начисления очков
- Выполнение привычки: +10 очков
- Streak 7 дней: +50 очков
- Streak 30 дней: +200 очков
- Победа в челлендже: +100 очков
- Создание групповой привычки: +25 очков
- Приглашение друга: +15 очков

// Система уровней
Level 1: 0-99 очков (Новичок)
Level 2: 100-299 очков (Энтузиаст)  
Level 3: 300-599 очков (Мастер)
Level 4: 600-999 очков (Эксперт)
Level 5: 1000+ очков (Легенда)
```

**Файлы для создания:**
```
domain/usecase/gamification/CalculatePointsUseCase.kt
domain/usecase/gamification/UpdateUserLevelUseCase.kt
domain/usecase/gamification/GetUserStatsUseCase.kt
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Очки начисляются автоматически
- ✅ Уровни обновляются корректно

---

### 🔧 Контрольная точка 4.2: Достижения и статистика (1 день)
**Задачи:**
- [ ] Создать систему достижений
- [ ] Реализовать социальную статистику
- [ ] Создать экраны статистики
- [ ] Добавить уведомления о достижениях

**Достижения:**
```kotlin
enum class Achievement {
    FIRST_HABIT("Первая привычка"),
    WEEK_STREAK("Неделя без пропусков"),
    MONTH_STREAK("Месяц постоянства"),
    SOCIAL_BUTTERFLY("Пригласил 5 друзей"),
    CHALLENGE_WINNER("Победитель челленджа"),
    GROUP_CREATOR("Создатель группы"),
    LEVEL_UP("Повышение уровня")
}
```

**Социальная статистика:**
```kotlin
// Рейтинги среди друзей
- Общий рейтинг по очкам
- Лучшие стрики
- Самые активные в группах
- Победители челленджей
- Еженедельные лидеры
```

**Файлы для создания:**
```
presentation/ui/stats/StatsScreen.kt
presentation/ui/stats/AchievementsScreen.kt
presentation/ui/stats/LeaderboardScreen.kt
domain/usecase/stats/GetLeaderboardUseCase.kt
domain/usecase/stats/GetAchievementsUseCase.kt
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Достижения разблокируются автоматически
- ✅ Статистика отображается корректно
- ✅ Рейтинги обновляются в реальном времени

---

## 🏗️ ЭТАП 5: Финальная интеграция и полировка (1 день)

### 📊 Цель этапа
Объединить все социальные функции, добавить финальные штрихи и провести тестирование.

### 🔧 Контрольная точка 5.1: Интеграция и тестирование (1 день)
**Задачи:**
- [ ] Интегрировать все социальные функции
- [ ] Обновить главный экран с социальными элементами
- [ ] Добавить анимации и микроинтеракции
- [ ] Провести end-to-end тестирование
- [ ] Оптимизировать производительность

**Финальные обновления:**
```kotlin
// HabitsScreen обновления
- Индикаторы групповых привычек
- Быстрый доступ к друзьям
- Уведомления о челленджах
- Социальная активность

// Навигация
- Bottom navigation с социальными разделами
- Deep links для приглашений
- Уведомления с навигацией
```

**Критерии завершения:**
- ✅ Проект собирается без ошибок
- ✅ Все социальные функции работают
- ✅ UI/UX отполирован
- ✅ Производительность оптимизирована
- ✅ Готов к релизу

---

## 📊 Итоговая статистика планируемой реализации

### 🕐 Временные рамки:
- **Этап 1 (Друзья)**: 2-3 дня
- **Этап 2 (Групповые привычки)**: 2-3 дня  
- **Этап 3 (Челленджи)**: 2 дня
- **Этап 4 (Геймификация)**: 1-2 дня
- **Этап 5 (Интеграция)**: 1 день
- **Общее время**: 8-11 дней

### 📁 Файлы для создания: ~40 файлов
- Модели: 5 файлов
- Repositories: 2 файла  
- Use Cases: 20 файлов
- UI Screens: 10 файлов
- ViewModels: 3 файла

### 🧪 Тестирование:
- Unit тесты для всех Use Cases
- UI тесты для критических flow
- Integration тесты для Firestore
- End-to-end тестирование социальных функций

### 🎯 Ожидаемый результат:
Полнофункциональный социальный трекер привычек с друзьями, групповыми привычками, челленджами и геймификацией, работающий только на Firebase Firestore без отдельного бэкенда.

---

## ✅ Готовность к началу реализации

Этот план готов к выполнению. Каждая контрольная точка имеет четкие критерии завершения и гарантирует, что проект будет собираться без ошибок на каждом этапе.

**Следующий шаг**: Начать с Этапа 1, Контрольной точки 1.1 - создание моделей данных для системы друзей.
