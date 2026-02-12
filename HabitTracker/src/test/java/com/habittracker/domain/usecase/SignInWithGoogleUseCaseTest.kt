package com.habittracker.domain.usecase

import android.content.Context
import android.content.Intent
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.IOException

@ExperimentalCoroutinesApi
class SignInWithGoogleUseCaseTest {

    @Mock
    private lateinit var authRepository: AuthRepository

    @Mock
    private lateinit var mockContext: Context

    private lateinit var signInWithGoogleUseCase: SignInWithGoogleUseCase

    private val testUser = User(
        id = "test-user-id",
        name = "Test User",
        email = "test@example.com",
        avatarUrl = "https://example.com/avatar.jpg",
        level = 1,
        points = 0,
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        lastActiveAt = Clock.System.now().toEpochMilliseconds()
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        signInWithGoogleUseCase = SignInWithGoogleUseCase(authRepository)
    }

    @Test
    fun `invoke should return success when Google sign-in is successful`() = runTest {
        // Arrange
        val successFlow = flow { emit(Result.success<Intent?>(null)) }
        Mockito.`when`(authRepository.signInWithGoogle(mockContext)).thenReturn(successFlow)

        // Act
        val result = signInWithGoogleUseCase(mockContext)

        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isSuccess)
        assertNull(flowResult.getOrNull())
        Mockito.verify(authRepository).signInWithGoogle(mockContext)
    }

    @Test
    fun `invoke should return error when Google sign-in fails`() = runTest {
        // Arrange
        val expectedException = IOException("Authentication failed")
        val errorFlow = flow { emit(Result.failure<Intent?>(expectedException)) }
        Mockito.`when`(authRepository.signInWithGoogle(mockContext)).thenReturn(errorFlow)

        // Act
        val result = signInWithGoogleUseCase(mockContext)

        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isFailure)
        assertEquals(expectedException, flowResult.exceptionOrNull())
        Mockito.verify(authRepository).signInWithGoogle(mockContext)
    }

    @Test
    fun `invoke should return error when authentication is cancelled`() = runTest {
        // Arrange
        val cancelledException = Exception("User cancelled authentication")
        val errorFlow = flow { emit(Result.failure<Intent?>(cancelledException)) }
        Mockito.`when`(authRepository.signInWithGoogle(mockContext)).thenReturn(errorFlow)

        // Act
        val result = signInWithGoogleUseCase(mockContext)

        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isFailure)
        assertEquals(cancelledException, flowResult.exceptionOrNull())
        Mockito.verify(authRepository).signInWithGoogle(mockContext)
    }
}
