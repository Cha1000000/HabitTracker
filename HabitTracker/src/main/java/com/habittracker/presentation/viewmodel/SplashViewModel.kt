package com.habittracker.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.repository.AccountRepository
import com.habittracker.domain.usecase.account.PendingDeletionOutcome
import com.habittracker.domain.usecase.account.ResolvePendingAccountDeletionUseCase
import com.habittracker.domain.util.AnalyticsManager
import com.habittracker.domain.util.ErrorLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Разбирает прерванное удаление аккаунта, пока идёт анимация заставки. */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val resolvePendingDeletion: ResolvePendingAccountDeletionUseCase,
    private val accountRepository: AccountRepository,
    private val analyticsManager: AnalyticsManager,
    private val errorLogger: ErrorLogger
) : ViewModel() {

    /** `null`, пока проверка не закончилась. */
    private val _pendingDeletion = MutableStateFlow<PendingDeletionOutcome?>(null)
    val pendingDeletion: StateFlow<PendingDeletionOutcome?> = _pendingDeletion.asStateFlow()

    init {
        viewModelScope.launch {
            val outcome = try {
                resolvePendingDeletion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to resolve pending account deletion")
                // Отметка осталась — синк заблокирован. Отправляем на экран удаления, где можно
                // закончить или отказаться; иначе приложение тихо работало бы без синхронизации
                val markerLeft = runCatching { accountRepository.pendingDeletion() != null }
                    .onFailure { Timber.tag(TAG).e(it, "Failed to read deletion marker") }
                    // Не прочитали — считаем, что отметка есть: экран удаления даст выбор, а не тишину
                    .getOrDefault(true)
                if (markerLeft) PendingDeletionOutcome.NEEDS_CONFIRMATION else PendingDeletionOutcome.NONE
            }
            if (outcome == PendingDeletionOutcome.FINISHED) {
                // Как после удаления на экране: uid удалённого аккаунта больше не отправляем
                analyticsManager.setUserId(null)
                errorLogger.setUserId("")
            }
            _pendingDeletion.value = outcome
        }
    }

    private companion object {
        const val TAG = "SplashViewModel"
    }
}
