package com.habittracker.domain.usecase

import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.io.IOException

@ExperimentalCoroutinesApi
class SignOutUseCaseTest {

    @Mock
    private lateinit var authRepository: AuthRepository

    private lateinit var signOutUseCase: SignOutUseCase

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `invoke should return success when signOut is successful`() = runTest {
        // Arrange
        val successFlow = flow { emit(Result.success(Unit)) }
        Mockito.`when`(authRepository.signOut()).thenReturn(successFlow)
        signOutUseCase = SignOutUseCase(authRepository)
        
        // Act
        val result = signOutUseCase()
        
        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isSuccess)
        assertEquals(Unit, flowResult.getOrNull())
        Mockito.verify(authRepository).signOut()
    }

    @Test
    fun `invoke should return error when signOut fails`() = runTest {
        // Arrange
        val expectedException = IOException("Network error")
        val errorFlow = flow { emit(Result.failure<Unit>(expectedException)) }
        Mockito.`when`(authRepository.signOut()).thenReturn(errorFlow)
        signOutUseCase = SignOutUseCase(authRepository)

        // Act
        val result = signOutUseCase()
        
        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isFailure)
        assertEquals(expectedException, flowResult.exceptionOrNull())
        Mockito.verify(authRepository).signOut()
    }
}
