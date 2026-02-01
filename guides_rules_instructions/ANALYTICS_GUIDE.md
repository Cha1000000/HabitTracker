# Руководство по аналитике и мониторингу HabitTracker

В этом документе описано, как использовать внедренную систему аналитики, мониторинга производительности и сбора ошибок.

## 🛠 Используемые инструменты

*   **Firebase Analytics**: Бизнес-метрики и поведение пользователей (Глобальный трекинг).
*   **Yandex AppMetrica**: Альтернативная аналитика, продуктовые метрики, трекинг крэшей и ошибок.
*   **Firebase Crashlytics**: Отчеты о вылетах и некритичных ошибках.
*   **Firebase Performance**: Мониторинг быстродействия (запуск, сеть, экраны).
*   **Timber**: Интеллектуальное логирование (Console в Debug, Crashlytics + AppMetrica в Release).

## 📊 Где смотреть данные

1.  **Firebase Console**: [https://console.firebase.google.com/](https://console.firebase.google.com/)
    *   **Вылеты и ошибки**: Раздел `Release Monitor` -> `Crashlytics`.
    *   **Поведение пользователей**: Раздел `Analytics` -> `Dashboard` или `Events`.
    *   **Скорость работы**: Раздел `Release Monitor` -> `Performance`.
    *   **Отладка**: Раздел `Analytics` -> `DebugView`.

2.  **Yandex AppMetrica**: [https://appmetrica.yandex.ru/](https://appmetrica.yandex.ru/)
    *   **Дашборд**: Общая статистика по аудитории.
    *   **События**: Детальная информация по каждому событию (`Events`).
    *   **Крэши и ошибки**: Раздел `Crashes` и `Errors`.

## 🚨 Обработка ошибок и вылетов

Мы используем единый интерфейс `ErrorLogger`, реализация `CrashlyticsErrorLogger` отправляет данные одновременно в **Firebase Crashlytics** и **AppMetrica**.

### Как анализировать ошибку:

1.  **Crashlytics**:
    *   Посмотрите Stack Trace.
    *   Вкладка **Logs**: покажет последние действия пользователя (Timber логи).
    *   Вкладка **Keys**: покажет контекст (например, `user_id`, `screen_name`).

2.  **AppMetrica**:
    *   Раздел **Errors** (Ошибки) покажет логированные исключения (`AppMetrica.reportError`).
    *   Событие `error_occurred` содержит параметры `error_type` и `error_message`.

## 📈 Основные события (Events)

Все события отправляются параллельно в Firebase и AppMetrica через `AnalyticsManager`.

| Событие | Описание | Параметры |
| :--- | :--- | :--- |
| **Привычки** | | |
| `habit_created` | Создание привычки | `habit_id`, `habit_type` |
| `habit_deleted` | Удаление привычки | `habit_id`, `habit_type` |
| `habit_completed` | Отметка выполнения | `habit_id`, `value`, `is_done` |
| `group_habit_joined` | Присоединение к группе | `habit_id`, `method` (invite/code) |
| **Социальное** | | |
| `friend_request_accepted` | Принятие заявки в друзья | - |
| `participant_calendar_expanded` | Просмотр статистики друга | `habit_id`, `participant_id` |
| **Челленджи** | | |
| `challenge_created` | Создание челленджа | `challenge_id`, `habit_id` |
| `challenge_joined` | Вступление в челлендж | `challenge_id` |
| `challenge_completed` | Завершение челленджа | `challenge_id` |
| **Геймификация** | | |
| `achievement_unlocked` | Получение достижения | `achievement_id` |
| **Системные** | | |
| `screen_view` | Просмотр экрана | `screen_name`, `screen_class` |
| `login` / `sign_up` | Авторизация | `method` |
| `error_occurred` | Ошибка в приложении | `error_type`, `error_message` |

## 💡 Советы по разработке

*   **Единая точка входа**: Используйте интерфейс `AnalyticsManager` для отправки событий. Не вызывайте SDK напрямую.
    ```kotlin
    analyticsManager.logEvent(AnalyticsEvents.HABIT_CREATED, mapOf(...))
    ```
*   **Автоматический трекинг экранов**: Реализован в `MainActivity` через `NavController`. При добавлении нового экрана, убедитесь, что его route добавлен в `mapRouteToScreenName` в `MainActivity.kt`.
*   **Логирование ошибок**: Используйте `Timber.e(e, "message")` или `errorLogger.logError(e)`. Это отправит отчет и в Crashlytics, и в AppMetrica.
*   **DebugView**:
    *   Firebase: `adb shell setprop debug.firebase.analytics.app com.habittracker`
    *   AppMetrica: Логи в Logcat с тегом `AppMetrica`.
