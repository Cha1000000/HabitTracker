package com.habittracker.data.local

import android.content.Context
import com.habittracker.domain.model.DeletionStage
import com.habittracker.domain.model.PendingAccountDeletion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отметка «удаление аккаунта начато». Переживает убийство процесса: при следующем запуске по ней
 * удаление доводится до конца или отменяется. Пока отметка стоит, очередь синхронизации не
 * отправляется — иначе поздняя запись вернула бы в облако уже удалённый профиль.
 */
@Singleton
class AccountDeletionMarker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val _state by lazy { MutableStateFlow(load()) }
    val state: StateFlow<PendingAccountDeletion?> get() = _state.asStateFlow()

    val isActive: Boolean get() = _state.value != null

    /**
     * Ставит отметку синхронно (`commit`): следующий шаг удаления начнётся, только когда она на диске.
     *
     * @throws IOException отметку не удалось сохранить — удаление продолжать нельзя.
     */
    @Synchronized
    fun set(userId: String, stage: DeletionStage) {
        val saved = prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_STAGE, stage.name)
            .commit()
        if (!saved) throw IOException("Failed to persist account deletion marker")
        _state.value = PendingAccountDeletion(userId, stage)
    }

    @Synchronized
    fun clear() {
        // В памяти снимаем в любом случае: иначе синк остался бы заблокирован до перезапуска
        if (!prefs.edit().clear().commit()) {
            Timber.tag(TAG).e("Failed to clear account deletion marker on disk")
        }
        _state.value = null
    }

    private fun load(): PendingAccountDeletion? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val stageName = prefs.getString(KEY_STAGE, null)
        val stage = DeletionStage.entries.firstOrNull { it.name == stageName }
            // Непонятная стадия — считаем, что облако удалено не до конца: так решение остаётся за пользователем
            ?: DeletionStage.CLOUD_IN_PROGRESS.also { Timber.tag(TAG).w("Unknown deletion stage '$stageName'") }
        return PendingAccountDeletion(userId, stage)
    }

    private companion object {
        const val TAG = "AccountDeletionMarker"
        const val PREFS_NAME = "account_deletion"
        const val KEY_USER_ID = "uid"
        const val KEY_STAGE = "stage"
    }
}
