# 🔥 Настройка Firebase Cloud Messaging (FCM) для HabitTracker

## 📋 Что нужно настроить в Firebase Console

### 1. **Включение Cloud Messaging API**

#### Шаг 1: Откройте Firebase Console
1. Перейдите в [Firebase Console](https://console.firebase.google.com/)
2. Выберите ваш проект `habitttracker-505b6`

#### Шаг 2: Настройки проекта
1. Нажмите на **шестеренку** (⚙️) в левом меню
2. Выберите **Project Settings**
3. Перейдите на вкладку **Cloud Messaging**

#### Шаг 3: Получите Server Key
В разделе **Project credentials** найдите:
- **Server key** - это ключ, который нужно скопировать
- **Sender ID** - это ваш project number (637297267141)

### 2. **Включение Firebase Cloud Messaging API в Google Cloud**

#### Шаг 1: Google Cloud Console
1. Перейдите в [Google Cloud Console](https://console.cloud.google.com/)
2. Выберите проект `habitttracker-505b6`

#### Шаг 2: Включение API
1. Перейдите в **APIs & Services** → **Library**
2. Найдите и включите следующие API:
   - **Firebase Cloud Messaging API**
   - **Cloud Messaging** (если не включен)

### 3. **Настройка Server Key в приложении**

#### Замените ключ в build.gradle.kts:
```kotlin
// В defaultConfig
buildConfigField("String", "FCM_SERVER_KEY", "\"ВАША_СКОПИРОВАННАЯ_СТРОКА_СЕРВЕР_КЕЯ\"")

// В buildTypes -> debug
buildConfigField("String", "FCM_SERVER_KEY", "\"ВАША_СКОПИРОВАННАЯ_СТРОКА_СЕРВЕР_КЕЯ\"")
```

**Пример:**
```kotlin
buildConfigField("String", "FCM_SERVER_KEY", "\"AAAA1234567890:APA91bHsample_key_here\"")
```

## 🔧 Дополнительные настройки (опционально)

### 4. **Service Account для продакшена (рекомендуется)**

Для более безопасной отправки уведомлений:

#### Шаг 1: Создание Service Account
1. **Firebase Console** → **Project Settings** → **Service accounts**
2. Нажмите **Generate new private key**
3. Скачайте JSON файл

#### Шаг 2: Использование в приложении
```kotlin
// Вместо FCMApiClient можно использовать Firebase Admin SDK
// Это более безопасно для продакшена
```

### 5. **Настройка топиков (опционально)**

Для отправки уведомлений группам пользователей:

#### В Firebase Console:
1. **Cloud Messaging** → **Send your first message**
2. Выберите **Topic** вместо **User segment**
3. Создайте топики: `habit_reminders`, `group_achievements`, etc.

## 🚀 Проверка работы FCM

### 1. **Тестирование через Firebase Console**
1. **Cloud Messaging** → **Send your first message**
2. Выберите ваше приложение
3. Отправьте тестовое уведомление

### 2. **Проверка в логах приложения**
Запустите приложение и проверьте логи:
```
FCMTokenManager: FCM Token получен: [токен]
HabitTrackerFirebaseMessagingService: Получено уведомление
```

### 3. **Отладка проблем**
Если уведомления не приходят:
- Проверьте, что Server Key правильно настроен
- Убедитесь, что Firebase Cloud Messaging API включен
- Проверьте, что приложение имеет разрешение на уведомления
- Проверьте логи на наличие ошибок

## 📱 Как работает FCM в нашем приложении

### Архитектура:
```
Android App ←→ Firebase Firestore ←→ FCM Servers ←→ User Devices
```

### Процесс отправки уведомления:
1. Пользователь А выполняет групповую привычку
2. `MarkHabitCompletedUseCase` → `SendGroupHabitPushNotificationUseCase`
3. Получаем участников привычки из Firestore
4. Получаем их FCM токены из Firestore
5. `FCMApiClient` отправляет уведомления через FCM REST API
6. FCM серверы доставляют уведомления на устройства

### Типы уведомлений:
- **FRIEND_REQUEST** - Запрос в друзья
- **HABIT_INVITATION** - Приглашение в групповую привычку  
- **HABIT_COMPLETION** - Участник выполнил привычку
- **GROUP_ACHIEVEMENT** - Групповое достижение
- **HABIT_REMINDER** - Напоминание о привычке

## 🔒 Безопасность

### ⚠️ Важные замечания:
- Server Key хранится в приложении (не критично для нашего случая)
- Для продакшена рекомендуется использовать Firebase Admin SDK
- Не коммитьте реальные ключи в Git репозиторий
- Используйте переменные окружения для CI/CD

### 💡 Рекомендации для продакшена:
```kotlin
// В local.properties (не коммитится в Git)
FCM_SERVER_KEY=your_real_server_key_here

// В build.gradle.kts
buildConfigField("String", "FCM_SERVER_KEY", "\"${project.findProperty("FCM_SERVER_KEY") ?: ""}\"")
```

## ✅ Готово!

После выполнения всех шагов ваше приложение будет готово к отправке и получению push-уведомлений через Firebase Cloud Messaging!
