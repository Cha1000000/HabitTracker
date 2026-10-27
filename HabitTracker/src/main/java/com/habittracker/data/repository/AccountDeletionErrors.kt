package com.habittracker.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.habittracker.domain.model.NoConnectionException
import com.habittracker.domain.model.RequestRejectedException
import com.habittracker.domain.model.TooManyAttemptsException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Переводит ошибку Firebase при удалении аккаунта в доменную, чтобы экран показал понятную
 * причину. Неизвестные ошибки возвращаются как есть — для них общий текст.
 */
internal fun Exception.toDeletionError(): Exception = when (this) {
    is FirebaseNetworkException -> NoConnectionException(this)
    is FirebaseTooManyRequestsException -> TooManyAttemptsException(this)
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> NoConnectionException(this)
        // RESOURCE_EXHAUSTED — исчерпана дневная квота проекта: ждать, а не проверять сеть
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION,
        FirebaseFirestoreException.Code.UNAUTHENTICATED,
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> RequestRejectedException(this)
        else -> this
    }
    is StorageException -> when (errorCode) {
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> NoConnectionException(this)
        StorageException.ERROR_NOT_AUTHENTICATED,
        StorageException.ERROR_QUOTA_EXCEEDED -> RequestRejectedException(this)
        else -> this
    }
    is FirebaseAuthInvalidUserException ->
        if (errorCode == "ERROR_USER_DISABLED") RequestRejectedException(this) else this
    // Только сетевые IOException: ошибка диска или протокола — не повод проверять интернет
    is SocketException, is SocketTimeoutException, is UnknownHostException -> NoConnectionException(this)
    else -> this
}
