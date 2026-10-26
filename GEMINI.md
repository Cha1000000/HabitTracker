# HabitTracker - Project Context for Gemini

## Role Routing Rules

When handling tasks, apply role routing and skill mapping from:
`.agents/roles/Roles.md` (path relative to the repository root)

If the user explicitly asks for a role or names specific skills, that explicit instruction overrides automatic routing.


## 1. Project Overview
**HabitTracker** is a native Android application aimed at building habits through social interaction and gamification.
*   **Type:** Native Android App.
*   **Core Philosophy:** Offline-first, Social-centric, Gamified.
*   **Current State:** Active development. Features include personal/group habits, friend system, challenges, and basic gamification.

## 2. Tech Stack
*   **Language:** Kotlin (1.9+).
*   **UI:** Jetpack Compose (Material 3) with a custom "Tahoe" Design System.
*   **Architecture:** Clean Architecture + MVVM + Single Activity.
*   **Dependency Injection:** Hilt.
*   **Async/Concurrency:** Coroutines + Flow.
*   **Local Data:** Room (SQLite) - **The Single Source of Truth**.
*   **Remote Data:** Firebase Firestore (Realtime).
*   **Synchronization:** Custom Offline-first Outbox Pattern + WorkManager.
*   **Notifications:** Firebase Cloud Messaging (FCM) + Local WorkManager.
*   **Images:** Coil.
*   **Analytics/Crashlytics:** Firebase, AppMetrica.

## 3. Architecture & Data Flow

### 3.1. Clean Architecture Layers
*   **Presentation:** `com.habittracker.presentation`
    *   ViewModels, Compose Screens, Navigation, Theme (`Tahoe`).
*   **Domain:** `com.habittracker.domain`
    *   Business Logic, Data Models, Repository Interfaces, Use Cases.
    *   *Pure Kotlin, no Android dependencies preferred.*
*   **Data:** `com.habittracker.data`
    *   Repository Implementations, Room Entities, DTOs, Data Sources.

### 3.2. Synchronization Strategy (CRITICAL)
The app uses a strict **Room-First** approach.
1.  **Read:** UI observes Room DB (`Flow<List<Habit>>`).
2.  **Write:**
    *   User performs action (e.g., `MarkHabitCompleted`).
    *   Repository updates Room *immediately*.
    *   Repository enqueues a sync operation into the `sync_outbox` table in Room.
    *   `SyncManager` (via `SyncWorker`) pushes items from `sync_outbox` to Firestore.
3.  **Conflict Resolution:**
    *   **Pending Guard:** Incoming remote updates are ignored if a local operation for that entity is pending.
    *   **Last-Write-Wins (LWW):** Uses `updatedAtMillis` to resolve conflicts during merge.
    *   **Soft Deletes:** `Habit` and `Challenge` entities use `deletedAtMillis`/`isActive` flags.

## 4. Key Entities & Domain Models
*   **Habit:** Personal or Group. Has `frequency`, `reminders`, `goalType` (Simple/Measurable).
*   **HabitEntry:** Daily execution record. Keyed by `habitId + userId + date`.
*   **User:** Contains gamification stats (`level`, `points`, `streak`).
*   **Friendship / FriendRequest:** Manages social graph.
*   **Challenge:** Competitive wrapper for habits.

## 5. Directory Structure
*   `HabitTracker/`: Android App Module.
    *   `src/main/java/com/habittracker/`: Source code.
    *   `src/main/res/`: Resources (drawables, values).
*   `guides_rules_instructions/`: **Highly important architectural documentation.**
    *   `sync_architecture.md`: Deep dive into the sync logic.
    *   `FCM_SETUP_INSTRUCTIONS.md`: Push notification setup.

## 6. Build & Run
*   **JDK:** 17.
*   **SDK:** Compile 36, Min 26.
*   **Build Command:** `./gradlew assembleDebug`
*   **Run Tests:** `./gradlew test` (Unit), `./gradlew connectedDebugAndroidTest` (UI).
*   **Signing:** Configured in `build.gradle.kts` via `local.properties` or env vars.

## 7. Development Guidelines
*   **Style:** Kotlin Style Guide. Use trailing commas.
*   **Compose:** Avoid large build methods. Break down into smaller Composables. Use `Preview`s.
*   **State:** Hoist state to ViewModels. Use `StateFlow`.
*   **Testing:** Write Unit tests for UseCases and ViewModels.
*   **Safety:** Always validate nullable types. Do not use `!!`.

## 8. Specific Nuances
*   **Icons:** The app uses a mix of Material Icons and custom "Tahoe" icons (xml vectors).
*   **Time:** Uses `java.time` (LocalDate, etc.) and `kotlinx-datetime`.
*   **Secrets:** Never commit keys. Use `local.properties`.

## 9. Second-brain sync (start & end of session)

Vladimir keeps a **knowledge card** for this project in his Obsidian "second brain" (an LLM Wiki
in the spirit of Karpathy), **outside this repo**:

- CachyOS: `~/YandexDisk/Obsidian/My Vault/projects/Habit Tracker.md`
- Other OSes (macOS/Windows): the vault path differs — confirm with Vladimir or check the vault's
  `reference/Рабочие репозитории.md` registry.

**At the START of a session:** read that card first to recover prior context (past decisions,
what was done, open questions, working rules). Reading is read-only. If the path doesn't exist
(different OS / not synced), ask Vladimir for the vault path; don't block the work.

**At the END of a session:** proactively **ask** whether to record the outcome into the card.
If yes, append to `## Решения и заметки` (newest on top, dated), **in Russian**: 1–3 line summary
— what changed, key decisions, version/branch, follow-ups; add `[[wiki-links]]`, bump `updated:`.
Editing means writing to an absolute path (request permission). **Never** commit the card edit
into this repo's git — it belongs to the vault (synced by Yandex.Disk).

Reminder: **do not commit or push** until Vladimir has verified the build locally and approved.
