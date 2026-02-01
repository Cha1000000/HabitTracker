package com.habittracker.presentation.state

import androidx.compose.runtime.Immutable
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.User
import com.habittracker.domain.usecase.gamification.LeaderboardEntry

/**
 * UI состояние для экрана детальной информации о групповой привычке
 */
@Immutable
data class GroupHabitDetailUiState(
    val isLoading: Boolean = false,
    val currentUserId: String = "",
    val habit: Habit? = null,
    val participants: List<User> = emptyList(),
    val habitEntries: List<HabitEntry> = emptyList(),
    val pendingInvitations: List<HabitInvitation> = emptyList(),
    val inviteCode: String? = null,
    val isOwner: Boolean = false,
    val isManageMode: Boolean = false,
    val isGeneratingCode: Boolean = false,
    val groupStreak: Int = 0,
    val isCompletedToday: Boolean = false,
    val isMarkingCompletion: Boolean = false,
    val participantLeaderboard: List<LeaderboardEntry> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val showPhotoProofPrompt: Boolean = false, // Показать предложение добавить фото после выполнения
    val currentUserPhotoUrl: String? = null, // URL фото текущего пользователя за сегодня
    val isUploadingPhoto: Boolean = false, // Флаг загрузки фото
    val showPhotoSheet: Boolean = false, // Показать BottomSheet для добавления фото
    val nudgedUserIds: Set<String> = emptySet(), // ID пользователей, которым уже отправлен nudge (на cooldown)
    val isSendingNudge: Boolean = false // Флаг отправки nudge
)