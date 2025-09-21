package com.habittracker.domain.usecase.habit

import com.habittracker.domain.model.isPending
import com.habittracker.domain.repository.SocialRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case для управления жизненным циклом приглашений в привычки
 * Автоматически истекает старые приглашения и очищает базу данных
 */
@Singleton
class HabitInvitationManagerUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    companion object {
        private const val CHECK_INTERVAL_MINUTES = 60L // Проверяем каждый час
        private const val INVITATION_EXPIRY_DAYS = 7L // Приглашения истекают через 7 дней
    }

    /**
     * Запускает автоматическое управление приглашениями
     */
    fun startInvitationManagement() {
        if (isRunning) return
        
        isRunning = true
        scope.launch {
            while (isActive) {
                try {
                    // Истекаем старые приглашения
                    expireOldInvitations()
                    
                    // Очищаем очень старые приглашения из базы
                    cleanupOldInvitations()
                    
                    // Ждем до следующей проверки
                    delay(CHECK_INTERVAL_MINUTES * 60 * 1000)
                } catch (e: Exception) {
                    // Логируем ошибку, но продолжаем работу
                    // TODO: Добавить логирование
                    delay(CHECK_INTERVAL_MINUTES * 60 * 1000)
                }
            }
        }
    }

    /**
     * Останавливает автоматическое управление приглашениями
     */
    fun stopInvitationManagement() {
        isRunning = false
    }

    /**
     * Истекает приглашения старше INVITATION_EXPIRY_DAYS дней
     */
    private suspend fun expireOldInvitations() {
        socialRepository.expireOldHabitInvitations()
    }

    /**
     * Удаляет из базы приглашения старше 30 дней
     */
    private suspend fun cleanupOldInvitations() {
        // Реализация будет добавлена в SocialRepository
        // socialRepository.cleanupOldHabitInvitations()
    }

    /**
     * Вычисляет время истечения приглашения
     */
    fun calculateExpiryTime(createdAt: Instant = Clock.System.now()): Instant {
        val localDateTime = createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val newDate = localDateTime.date.plus(INVITATION_EXPIRY_DAYS.toInt(), DateTimeUnit.DAY)
        val newDateTime = newDate.atTime(localDateTime.hour, localDateTime.minute, localDateTime.second)
        return newDateTime.toInstant(TimeZone.currentSystemDefault())
    }

    /**
     * Проверяет, истекло ли приглашение
     */
    fun isInvitationExpired(expiresAt: Instant?): Boolean {
        return expiresAt != null && Clock.System.now() > expiresAt
    }

    /**
     * Принудительно истекает все приглашения для конкретной привычки
     * Используется при удалении или деактивации привычки
     */
    suspend fun expireInvitationsForHabit(habitId: String) {
        try {
            // Получаем все активные приглашения для привычки
            val invitations = socialRepository.getHabitInvitations(habitId)
            
            // Отменяем все pending приглашения
            invitations.collect { invitationsList ->
                invitationsList.filter { it.isPending() }.forEach { invitation ->
                    socialRepository.cancelHabitInvitation(invitation.id)
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки, но логируем
            // TODO: Добавить логирование
        }
    }

    /**
     * Принудительно отменяет все исходящие приглашения пользователя
     * Используется при деактивации аккаунта
     */
    suspend fun cancelAllUserInvitations(userId: String) {
        try {
            val outgoingInvitations = socialRepository.getOutgoingHabitInvitations(userId)
            
            outgoingInvitations.collect { invitationsList ->
                invitationsList.filter { it.isPending() }.forEach { invitation ->
                    socialRepository.cancelHabitInvitation(invitation.id)
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки, но логируем
            // TODO: Добавить логирование
        }
    }
}
