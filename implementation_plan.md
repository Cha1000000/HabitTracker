# План реализации: Создание и отображение привычек

## 1. Domain Layer (Доменный слой)
- [x] Создать/доработать модель данных `Habit` в `domain/model`.
- [x] Определить интерфейс `HabitRepository` в `domain/repository`.
- [x] Создать `UseCase` для добавления привычки: `AddHabitUseCase`.

## 2. Data Layer (Слой данных)
- [x] Реализовать `HabitRepositoryImpl` в `data/repository`.
- [x] Настроить `Room Database` и создать `HabitDao`.

## 3. Presentation Layer (Слой представления)
- [x] Создать `HabitsViewModel` для управления состоянием экрана.
- [x] Разработать UI-экран `HabitsScreen` с использованием Jetpack Compose.

## 4. Dependency Injection (Внедрение зависимостей)
- [x] Настроить Hilt-модули для предоставления `HabitRepository`, `UseCase` и `HabitDao`.
