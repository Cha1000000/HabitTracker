package com.habittracker.data.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.SyncOutboxDao
import com.habittracker.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import timber.log.Timber

/**
 * SyncManager: Outbox-подход для надежной синхронизации Room -> Firestore
 */
@Singleton
class SyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val outboxDao: SyncOutboxDao
) {
    /** Пока `true`, очередь не отправляется (например, на время удаления аккаунта). */
    @Volatile
    private var paused = false

    /** Дренажи идут по одному — так [pauseAndAwait] может дождаться текущего. */
    private val drainMutex = Mutex()

    /**
     * Останавливает отправку очереди и дожидается окончания текущего дренажа.
     * Новые операции копятся в outbox и уйдут после [resume].
     *
     * @return false, если текущий дренаж не закончился за отведённое время; пауза тогда снята.
     */
    suspend fun pauseAndAwait(): Boolean {
        paused = true
        // Оффлайн запись в Firestore может висеть до появления сети — долго не ждём
        val drained = withTimeoutOrNull(PAUSE_WAIT_MS) { drainMutex.withLock { } } != null
        if (!drained) paused = false
        return drained
    }

    fun resume() {
        paused = false
    }

    private companion object {
        const val PAUSE_WAIT_MS = 10_000L
    }

    // Публичные API для репозиториев
    suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        payloadJson: String,
        updatedAt: Long,
        operationId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        Timber.tag("SyncManager").d(
            "[enqueueUpsert] type=$entityType id=$entityId opId=$operationId updatedAt=$updatedAt payloadSize=${payloadJson.length}"
        )
        outboxDao.insert(
            SyncOutboxEntity(
                operationId = operationId,
                entityType = entityType,
                entityId = entityId,
                opType = "UPSERT",
                payloadJson = payloadJson,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun enqueueArrayUnion(
        entityType: String,
        entityId: String,
        fieldPath: String,
        values: List<String>,
        updatedAt: Long,
        operationId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("fieldPath", fieldPath)
            .put("values", JSONArray(values))
            .toString()
        outboxDao.insert(
            SyncOutboxEntity(
                operationId = operationId,
                entityType = entityType,
                entityId = entityId,
                opType = "ARRAY_UNION",
                payloadJson = payload,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun enqueueArrayRemove(
        entityType: String,
        entityId: String,
        fieldPath: String,
        values: List<String>,
        updatedAt: Long,
        operationId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("fieldPath", fieldPath)
            .put("values", JSONArray(values))
            .toString()
        outboxDao.insert(
            SyncOutboxEntity(
                operationId = operationId,
                entityType = entityType,
                entityId = entityId,
                opType = "ARRAY_REMOVE",
                payloadJson = payload,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
        updatedAt: Long,
        operationId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        Timber.tag("SyncManager").d(
            "[enqueueDelete] type=$entityType id=$entityId opId=$operationId updatedAt=$updatedAt"
        )
        outboxDao.insert(
            SyncOutboxEntity(
                operationId = operationId,
                entityType = entityType,
                entityId = entityId,
                opType = "DELETE",
                payloadJson = "{}",
                updatedAt = updatedAt
            )
        )
    }

    // Плановый безопасный DELETE после soft-delete (gated по notBefore, с ограничением попыток)
    suspend fun enqueueSafeDelete(
        entityType: String,
        entityId: String,
        notBeforeMillis: Long,
        maxRetries: Int = 3,
        updatedAt: Long = System.currentTimeMillis(),
        operationId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("notBefore", notBeforeMillis)
            .put("maxRetries", maxRetries)
            .toString()
        Timber.tag("SyncManager").d(
            "[enqueueSafeDelete] type=$entityType id=$entityId opId=$operationId notBefore=$notBeforeMillis maxRetries=$maxRetries"
        )
        outboxDao.insert(
            SyncOutboxEntity(
                operationId = operationId,
                entityType = entityType,
                entityId = entityId,
                opType = "DELETE",
                payloadJson = payload,
                updatedAt = updatedAt
            )
        )
    }

    // Проверяет, есть ли pending операции для конкретной сущности
    suspend fun hasPendingOperations(entityType: String, entityId: String): Boolean = withContext(Dispatchers.IO) {
        outboxDao.hasPendingForEntity(entityType, entityId)
    }

    // Дренаж очереди: вызывать WorkManager'ом периодически и вручную при старте/онбординге
    suspend fun drainPending(maxBatch: Int = 50) = withContext(Dispatchers.IO) {
        drainMutex.withLock { drainLocked(maxBatch) }
    }

    private suspend fun drainLocked(maxBatch: Int) {
        if (paused) {
            Timber.tag("SyncManager").d("[drainPending] paused, skip")
            return
        }
        val batch = outboxDao.getByState("PENDING", maxBatch)
        Timber.tag("SyncManager").d("[drainPending] fetched=${batch.size}")
        for (item in batch) {
            if (paused) break
            try {
                Timber.tag("SyncManager").d(
                    "[drainPending] process id=${item.id} op=${item.opType} type=${item.entityType} entity=${item.entityId} updatedAt=${item.updatedAt}"
                )
                // Для отложенных DELETE — пропускаем до наступления notBefore
                if (item.opType == "DELETE") {
                    try {
                        val payloadObj = JSONObject(item.payloadJson)
                        if (payloadObj.has("notBefore")) {
                            val notBefore = payloadObj.getLong("notBefore")
                            val now = System.currentTimeMillis()
                            if (now < notBefore) {
                                Timber.tag("SyncManager").d("[drainPending] skip DELETE id=${item.id} until notBefore=$notBefore")
                                continue
                            }
                        }
                    } catch (e: Exception) {
                        // игнорируем, нет валидного payload — выполняем немедленно, но залогируем для диагностики
                        Timber.tag("SyncManager").w("[drainPending] invalid DELETE payload for id=${item.id}, executing immediately: ${e.message}")
                    }
                }
                outboxDao.updateState(item.id, "IN_PROGRESS")
                when (item.opType) {
                    "UPSERT" -> pushUpsert(item)
                    "DELETE" -> pushDelete(item)
                    "ARRAY_UNION" -> pushArrayUpdate(item, isUnion = true)
                    "ARRAY_REMOVE" -> pushArrayUpdate(item, isUnion = false)
                }
                outboxDao.deleteById(item.id)
                Timber.tag("SyncManager").d("[drainPending] done id=${item.id}")
            } catch (e: Exception) {
                val nowTs = System.currentTimeMillis()
                // Для DELETE учитываем лимит попыток
                if (item.opType == "DELETE") {
                    var maxRetries = 3
                    try {
                        val payloadObj = JSONObject(item.payloadJson)
                        if (payloadObj.has("maxRetries")) maxRetries = payloadObj.getInt("maxRetries")
                    } catch (e: Exception) {
                        Timber.tag("SyncManager").w("[drainPending] failed to parse maxRetries for id=${item.id}: ${e.message}")
                    }
                    val nextState = if (item.retryCount + 1 >= maxRetries) "FAILED" else "PENDING"
                    outboxDao.markRetry(item.id, e.message, nextState, nowTs)
                    Timber.tag("SyncManager").w(
                        e,
                        "[drainPending] DELETE failed id=${item.id} retries=${item.retryCount + 1}/$maxRetries -> $nextState: ${e.message}"
                    )
                } else {
                    // Для UPSERT — оставляем в PENDING, чтобы WorkManager повторял
                    outboxDao.markRetry(item.id, e.message, "PENDING", nowTs)
                    Timber.tag("SyncManager").w(
                        e,
                        "[drainPending] UPSERT failed id=${item.id} -> PENDING retry: ${e.message}"
                    )
                }
            }
        }
    }

    // --- Internal helpers ---
    private suspend fun pushUpsert(item: SyncOutboxEntity) {
        val (collection, docId) = route(item.entityType, item.entityId)
        val map = jsonToMap(item.payloadJson)
        Timber.tag("SyncManager").d("[pushUpsert] ${item.entityType} -> $collection/$docId payloadKeys=${map.keys}")
        val docRef = firestore.collection(collection).document(docId)
        // Принудительно обновляем только актуальный флаг активности isActive
        val flagUpdates = mutableMapOf<String, Any>()
        map["isActive"]?.let { flagUpdates["isActive"] = it }
        if (flagUpdates.isNotEmpty()) {
            try {
                Timber.tag("SyncManager").d("[pushUpsert] update flags=$flagUpdates")
                docRef.update(flagUpdates as Map<String, Any>)
                    .awaitUnit()
                Timber.tag("SyncManager").d("[pushUpsert] flags updated")
            } catch (e: Exception) {
                Timber.tag("SyncManager").w("[pushUpsert] flag update failed, will rely on merge(): ${e.message}")
            }
        }
        // Затем делаем общий merge всех полей
        docRef.set(map, com.google.firebase.firestore.SetOptions.merge())
            .awaitUnit()
        Timber.tag("SyncManager").d("[pushUpsert] DONE ${item.entityType} -> $collection/$docId")
    }

    private suspend fun pushDelete(item: SyncOutboxEntity) {
        val (collection, docId) = route(item.entityType, item.entityId)
        Timber.tag("SyncManager").d("[pushDelete] ${item.entityType} -> $collection/$docId")
        when (item.entityType) {
            // Soft delete для сущностей с поддержкой tombstones
            "habit", "challenge" -> {
                val updates = mapOf(
                    "isActive" to false,
                    "deletedAtMillis" to item.updatedAt,
                    "updatedAtMillis" to item.updatedAt
                )
                firestore.collection(collection)
                    .document(docId)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .awaitUnit()
            }
            "habit_entry", "friendship", "habit_invitation" -> {
                // Soft delete: устанавливаем deletedAtMillis вместо физического удаления
                val updates = mapOf(
                    "deletedAtMillis" to item.updatedAt,
                    "updatedAtMillis" to item.updatedAt
                )
                firestore.collection(collection)
                    .document(docId)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    .awaitUnit()
            }
            else -> {
                // Hard delete для остальных сущностей (friend_request, user_achievement и т.д.)
                firestore.collection(collection)
                    .document(docId)
                    .delete()
                    .awaitUnit()
            }
        }
        Timber.tag("SyncManager").d("[pushDelete] DONE ${item.entityType} -> $collection/$docId")
    }

    private suspend fun pushArrayUpdate(item: SyncOutboxEntity, isUnion: Boolean) {
        val (collection, docId) = route(item.entityType, item.entityId)
        val payloadObj = JSONObject(item.payloadJson)
        val fieldPath = payloadObj.getString("fieldPath")
        val valuesArr = payloadObj.optJSONArray("values") ?: JSONArray()
        val values = mutableListOf<String>()
        for (i in 0 until valuesArr.length()) {
            val v = valuesArr.optString(i)
            if (v.isNotBlank()) values.add(v)
        }
        if (values.isEmpty()) return

        val op = if (isUnion) {
            FieldValue.arrayUnion(*values.toTypedArray())
        } else {
            FieldValue.arrayRemove(*values.toTypedArray())
        }

        firestore.collection(collection)
            .document(docId)
            .update(
                mapOf(
                    fieldPath to op,
                    "updatedAtMillis" to item.updatedAt
                )
            )
            .awaitUnit()
    }

    private fun route(entityType: String, entityId: String): Pair<String, String> = when (entityType) {
        "user" -> "users" to entityId
        "friendship" -> "friendships" to entityId
        "friend_request" -> "friend_requests" to entityId
        "habit" -> "habits" to entityId
        "habit_entry" -> "habit_entries" to entityId
        "habit_invitation" -> "habit_invitations" to entityId
        "challenge" -> "challenges" to entityId
        "user_achievement" -> "user_achievements" to entityId
        "habit_participant_settings" -> {
            // entityId expected format: "habitId:userId"
            val parts = entityId.split(":")
            if (parts.size == 2) {
                "habits/${parts[0]}/participants/${parts[1]}/meta" to "settings"
            } else {
                // Fallback or error logging
                Timber.tag("SyncManager").e("Invalid entityId format for habit_participant_settings: $entityId")
                "invalid_path" to entityId
            }
        }
        else -> entityType + "s" to entityId // на всякий случай
    }

    // Простой конвертер JSON -> Map для Firestore
    private fun jsonToMap(json: String): Map<String, Any?> {
        val obj = JSONObject(json)
        return jsonObjectToMap(obj)
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until arr.length()) {
            val value = arr.get(i)
            list.add(
                when (value) {
                    is JSONObject -> jsonObjectToMap(value)
                    is JSONArray -> jsonArrayToList(value)
                    JSONObject.NULL -> null
                    else -> value
                }
            )
        }
        return list
    }
}

// Await helpers without bringing full kotlinx-coroutines-play-services if not already present
private suspend fun com.google.android.gms.tasks.Task<Void>.awaitUnit() {
    suspendCancellableAwait(this)
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return suspendCancellableAwait(this)
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <T> suspendCancellableAwait(task: com.google.android.gms.tasks.Task<T>): T {
    return suspendCancellableCoroutine { cont ->
        task.addOnCompleteListener { t ->
            if (t.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(t.result as T)
            } else {
                cont.resumeWithException(t.exception ?: RuntimeException("Task failed"))
            }
        }
        cont.invokeOnCancellation { tryCancel() }
    }
}

private fun tryCancel() {
    // Ничего, просто для совместимости
}
