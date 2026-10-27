package com.habittracker.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.habittracker.domain.model.NoConnectionException
import com.habittracker.domain.model.RequestRejectedException
import com.habittracker.domain.model.TooManyAttemptsException
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

class AccountDeletionErrorsTest {

    @Test
    fun network_failures_become_no_connection() {
        assertTrue(FirebaseNetworkException("offline").toDeletionError() is NoConnectionException)
        assertTrue(firestore(FirebaseFirestoreException.Code.UNAVAILABLE).toDeletionError() is NoConnectionException)
        assertTrue(firestore(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED).toDeletionError() is NoConnectionException)
        assertTrue(SocketException("reset").toDeletionError() is NoConnectionException)
        assertTrue(UnknownHostException("dns").toDeletionError() is NoConnectionException)
    }

    @Test
    fun throttling_becomes_too_many_attempts() {
        assertTrue(FirebaseTooManyRequestsException("slow down").toDeletionError() is TooManyAttemptsException)
    }

    @Test
    fun server_refusals_become_rejected() {
        listOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
        ).forEach { code ->
            assertTrue("$code", firestore(code).toDeletionError() is RequestRejectedException)
        }
        val disabled = FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled")
        assertTrue(disabled.toDeletionError() is RequestRejectedException)
    }

    @Test
    fun unknown_errors_pass_through() {
        val internal = firestore(FirebaseFirestoreException.Code.INTERNAL)
        assertSame(internal, internal.toDeletionError())
        val expired = FirebaseAuthInvalidUserException("ERROR_USER_TOKEN_EXPIRED", "expired")
        assertSame(expired, expired.toDeletionError())
        val other = IllegalStateException("bug")
        assertSame(other, other.toDeletionError())
        // Ошибка диска — не сеть
        val disk = IOException("disk full")
        assertSame(disk, disk.toDeletionError())
    }

    private fun firestore(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException("test", code)
}
