package com.habittracker.domain.usecase.social

import com.habittracker.domain.model.Nudge
import com.habittracker.domain.repository.AuthRepository
import com.habittracker.domain.repository.HabitRepository
import com.habittracker.domain.repository.NudgeRepository
import com.habittracker.domain.repository.SocialRepository
import com.habittracker.domain.repository.UserRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * UseCase для отправки "пинга" (nudge) участнику групповой привычки
 * Проверяет cooldown (1 час) перед отправкой и отправляет push-уведомление
 */
class SendNudgeUseCase @Inject constructor(
    private val nudgeRepository: NudgeRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val habitRepository: HabitRepository,
    private val socialRepository: SocialRepository
) {
    
    sealed class Result {
        data object Success : Result()
        data object OnCooldown : Result()
        data class Error(val message: String) : Result()
    }
    
    suspend operator fun invoke(
        habitId: String,
        receiverId: String
    ): Result {
        val senderId = authRepository.getCurrentUserId()
            ?: return Result.Error("User not authenticated")
        
        if (senderId == receiverId) {
            return Result.Error("Cannot nudge yourself")
        }
        
        val canSend = nudgeRepository.canSendNudge(habitId, senderId, receiverId)
        if (!canSend) {
            return Result.OnCooldown
        }
        
        val nudge = Nudge(
            habitId = habitId,
            senderId = senderId,
            receiverId = receiverId,
            sentAt = System.currentTimeMillis()
        )
        
        return nudgeRepository.sendNudge(nudge).fold(
            onSuccess = {
                // Отправляем push-уведомление получателю
                sendPushNotification(senderId, receiverId, habitId)
                Result.Success
            },
            onFailure = { Result.Error(it.message ?: "Unknown error") }
        )
    }
    
    private suspend fun sendPushNotification(senderId: String, receiverId: String, habitId: String) {
        try {
            Timber.tag("SendNudgeUseCase").d("sendPushNotification: senderId=$senderId, receiverId=$receiverId, habitId=$habitId")
            
            // Используем getUserFromFirestore для гарантированного получения данных
            val sender = userRepository.getUserFromFirestore(senderId) 
                ?: userRepository.getUserById(senderId)
            Timber.tag("SendNudgeUseCase").d("sender=${sender?.name ?: "NULL"}")
            
            val habit = habitRepository.getHabitById(habitId)
            Timber.tag("SendNudgeUseCase").d("habit=${habit?.title ?: "NULL"}")
            
            if (sender != null && habit != null) {
                Timber.tag("SendNudgeUseCase").d("Calling socialRepository.sendNudgeNotification...")
                socialRepository.sendNudgeNotification(
                    toUserId = receiverId,
                    fromUserName = sender.name,
                    habitTitle = habit.title,
                    habitId = habitId
                )
                Timber.tag("SendNudgeUseCase").d("sendNudgeNotification called successfully")
            } else {
                Timber.tag("SendNudgeUseCase").w("Cannot send nudge push: sender or habit is null (sender=${sender != null}, habit=${habit != null})")
            }
        } catch (e: Exception) {
            Timber.tag("SendNudgeUseCase").e(e, "Failed to send nudge push notification")
        }
    }
}
