package com.habittracker.domain.usecase

import android.content.Intent
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
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

    private lateinit var signInWithGoogleUseCase: SignInWithGoogleUseCase

    private val testUser = User(
        id = "test-user-id",
        name = "Test User",
        email = "test@example.com",
        avatarUrl = "https://example.com/avatar.jpg",
        level = 1,
        points = 0,
        createdAt = kotlinx.datetime.Clock.System.now(),
        lastActiveAt = kotlinx.datetime.Clock.System.now()
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
        Mockito.`when`(authRepository.signInWithGoogle()).thenReturn(successFlow)

        // Act
        val result = signInWithGoogleUseCase()

        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isSuccess)
        assertNull(flowResult.getOrNull())
        Mockito.verify(authRepository).signInWithGoogle()
    }

    @Test
    fun `invoke should return error when Google sign-in fails`() = runTest {
        // Arrange
        val expectedException = IOException("Authentication failed")
        val errorFlow = flow { emit(Result.failure<Intent?>(expectedException)) }
        Mockito.`when`(authRepository.signInWithGoogle()).thenReturn(errorFlow)

        // Act
        val result = signInWithGoogleUseCase()

        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isFailure)
        assertEquals(expectedException, flowResult.exceptionOrNull())
        Mockito.verify(authRepository).signInWithGoogle()
    }

    @Test
    fun `invoke should return error when authentication is cancelled`() = runTest {
        // Arrange
        val cancelledException = Exception("User cancelled authentication")
        val errorFlow = flow { emit(Result.failure<Intent?>(cancelledException)) }
        Mockito.`when`(authRepository.signInWithGoogle()).thenReturn(errorFlow)

        // Act
        val result = signInWithGoogleUseCase()

        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isFailure)
        assertEquals(cancelledException, flowResult.exceptionOrNull())
        Mockito.verify(authRepository).signInWithGoogle()
    }
}
