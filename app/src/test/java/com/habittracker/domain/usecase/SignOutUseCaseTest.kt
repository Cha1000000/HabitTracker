package com.habittracker.domain.usecase

import com.habittracker.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SignOutUseCaseTest {
    
    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()
    
    private val authRepository: AuthRepository = mock()
    private lateinit var signOutUseCase: SignOutUseCase

    @Test
    fun `invoke should return success when logout is successful`() = runTest {
        // Arrange
        Mockito.`when`(authRepository.logout()).then {}
        signOutUseCase = SignOutUseCase(authRepository)
        
        // Act
        val result = signOutUseCase()
        
        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isSuccess)
        assertEquals(Unit, flowResult.getOrNull())
        Mockito.verify(authRepository).logout()
    }

    @Test
    fun `invoke should return error when logout fails`() = runTest {
        // Arrange
        val expectedException = IOException("Network error")
        Mockito.`when`(authRepository.logout()).then { throw expectedException }
        signOutUseCase = SignOutUseCase(authRepository)

        // Act
        val result = signOutUseCase()
        
        // Collect the flow and get the first result
        val flowResult = result.first()

        // Assert
        assertTrue(flowResult.isFailure)
        assertEquals(expectedException, flowResult.exceptionOrNull())
    }
}
