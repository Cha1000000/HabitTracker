package com.habittracker.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.habittracker.data.local.dao.SyncOutboxDao
import com.habittracker.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * SyncManager: Outbox-подход для надежной синхронизации Room -> Firestore
 */
@Singleton
class SyncManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val outboxDao: SyncOutboxDao
) {
    // Публичные API для репозиториев
    suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        payloadJson: String,
        updatedAt: Long,
        operationId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        Log.d(
            "SyncManager",
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

    suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
        updatedAt: Long,
        operationId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        Log.d(
            "SyncManager",
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
        Log.d(
            "SyncManager",
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

    // Дренаж очереди: вызывать WorkManager'ом периодически и вручную при старте/онбординге
    suspend fun drainPending(maxBatch: Int = 50) = withContext(Dispatchers.IO) {
        val batch = outboxDao.getByState("PENDING", maxBatch)
        Log.d("SyncManager", "[drainPending] fetched=${batch.size}")
        for (item in batch) {
            try {
                Log.d(
                    "SyncManager",
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
                                Log.d("SyncManager", "[drainPending] skip DELETE id=${item.id} until notBefore=$notBefore")
                                continue
                            }
                        }
                    } catch (_: Exception) {
                        // игнорируем, нет валидного payload — выполняем немедленно
                    }
                }
                outboxDao.updateState(item.id, "IN_PROGRESS")
                when (item.opType) {
                    "UPSERT" -> pushUpsert(item)
                    "DELETE" -> pushDelete(item)
                }
                outboxDao.deleteById(item.id)
                Log.d("SyncManager", "[drainPending] done id=${item.id}")
            } catch (e: Exception) {
                val nowTs = System.currentTimeMillis()
                // Для DELETE учитываем лимит попыток
                if (item.opType == "DELETE") {
                    var maxRetries = 3
                    try {
                        val payloadObj = JSONObject(item.payloadJson)
                        if (payloadObj.has("maxRetries")) maxRetries = payloadObj.getInt("maxRetries")
                    } catch (_: Exception) {}
                    val nextState = if (item.retryCount + 1 >= maxRetries) "FAILED" else "PENDING"
                    outboxDao.markRetry(item.id, e.message, nextState, nowTs)
                    Log.w(
                        "SyncManager",
                        "[drainPending] DELETE failed id=${item.id} retries=${item.retryCount + 1}/$maxRetries -> $nextState: ${e.message}",
                        e
                    )
                } else {
                    // Для UPSERT — оставляем в PENDING, чтобы WorkManager повторял
                    outboxDao.markRetry(item.id, e.message, "PENDING", nowTs)
                    Log.w(
                        "SyncManager",
                        "[drainPending] UPSERT failed id=${item.id} -> PENDING retry: ${e.message}",
                        e
                    )
                }
            }
        }
    }

    // --- Internal helpers ---
    private suspend fun pushUpsert(item: SyncOutboxEntity) {
        val (collection, docId) = route(item.entityType, item.entityId)
        val map = jsonToMap(item.payloadJson)
        Log.d("SyncManager", "[pushUpsert] ${item.entityType} -> $collection/$docId payloadKeys=${map.keys}")
        val docRef = firestore.collection(collection).document(docId)
        // Если есть флаги активности — принудительно обновляем их отдельно для надёжности
        val flagUpdates = mutableMapOf<String, Any>()
        map["isActive"]?.let { flagUpdates["isActive"] = it }
        map["active"]?.let { flagUpdates["active"] = it }
        if (flagUpdates.isNotEmpty()) {
            try {
                Log.d("SyncManager", "[pushUpsert] update flags=$flagUpdates")
                docRef.update(flagUpdates as Map<String, Any>)
                    .awaitUnit()
                Log.d("SyncManager", "[pushUpsert] flags updated")
            } catch (e: Exception) {
                Log.w("SyncManager", "[pushUpsert] flag update failed, will rely on merge(): ${e.message}")
            }
        }
        // Затем делаем общий merge всех полей
        docRef.set(map, com.google.firebase.firestore.SetOptions.merge())
            .awaitUnit()
        Log.d("SyncManager", "[pushUpsert] DONE ${item.entityType} -> $collection/$docId")
    }

    private suspend fun pushDelete(item: SyncOutboxEntity) {
        val (collection, docId) = route(item.entityType, item.entityId)
        Log.d("SyncManager", "[pushDelete] ${item.entityType} -> $collection/$docId")
        firestore.collection(collection)
            .document(docId)
            .delete()
            .awaitUnit()
        Log.d("SyncManager", "[pushDelete] DONE ${item.entityType} -> $collection/$docId")
    }

    private fun route(entityType: String, entityId: String): Pair<String, String> = when (entityType) {
        "user" -> "users" to entityId
        "friendship" -> "friendships" to entityId
        "friend_request" -> "friend_requests" to entityId
        "habit" -> "habits" to entityId
        "habit_entry" -> "habit_entries" to entityId
        "habit_invitation" -> "habit_invitations" to entityId
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

private suspend fun <T> suspendCancellableAwait(task: com.google.android.gms.tasks.Task<T>): T {
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        task.addOnCompleteListener { t ->
            if (t.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(t.result as T) {}
            } else {
                cont.resumeWithException(t.exception ?: RuntimeException("Task failed"))
            }
        }
        cont.invokeOnCancellation { task.tryCancel() }
    }
}

private fun com.google.android.gms.tasks.Task<*>.tryCancel() {
    // Ничего, просто для совместимости
}
