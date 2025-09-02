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
                outboxDao.updateState(item.id, "IN_PROGRESS")
                when (item.opType) {
                    "UPSERT" -> pushUpsert(item)
                    "DELETE" -> pushDelete(item)
                }
                outboxDao.deleteById(item.id)
                Log.d("SyncManager", "[drainPending] done id=${item.id}")
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                outboxDao.markRetry(item.id, e.message, "FAILED", now)
                Log.w(
                    "SyncManager",
                    "[drainPending] failed id=${item.id} type=${item.entityType} entity=${item.entityId}: ${e.message}",
                    e
                )
            }
        }
    }

    // --- Internal helpers ---
    private suspend fun pushUpsert(item: SyncOutboxEntity) {
        val (collection, docId) = route(item.entityType, item.entityId)
        val map = jsonToMap(item.payloadJson)
        Log.d("SyncManager", "[pushUpsert] ${item.entityType} -> $collection/$docId")
        firestore.collection(collection)
            .document(docId)
            .set(map, com.google.firebase.firestore.SetOptions.merge())
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
