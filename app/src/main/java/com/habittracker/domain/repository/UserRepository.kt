package com.habittracker.domain.repository

import com.habittracker.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    
    suspend fun insertUser(user: User)
    
    suspend fun updateUser(user: User)
    
    suspend fun deleteUser(userId: String)
    
    suspend fun getUserById(userId: String): User?
    
    suspend fun getUserByEmail(email: String): User?
    
    fun getAllUsers(): Flow<List<User>>
    
    suspend fun getCurrentUser(): User?
    
    suspend fun setCurrentUser(userId: String)
    
    suspend fun searchUsersByName(query: String): List<User>
    
    suspend fun updateUserLevel(userId: String, level: Int, points: Int)
}
