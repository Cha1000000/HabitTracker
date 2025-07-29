package com.habittracker.data.repository

import com.habittracker.data.local.UserDao
import com.habittracker.data.local.entity.toDomain
import com.habittracker.data.local.entity.toEntity
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {
    
    override suspend fun insertUser(user: User) {
        userDao.insertUser(user.toEntity())
    }
    
    override suspend fun updateUser(user: User) {
        userDao.updateUser(user.toEntity())
    }
    
    override suspend fun deleteUser(userId: String) {
        val user = userDao.getUserById(userId)
        if (user != null) {
            userDao.deleteUser(user)
        }
    }
    
    override suspend fun getUserById(userId: String): User? {
        return userDao.getUserById(userId)?.toDomain()
    }
    
    override suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)?.toDomain()
    }
    
    override fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getCurrentUser(): User? {
        // TODO: Implement current user logic (SharedPreferences or similar)
        return null
    }
    
    override suspend fun setCurrentUser(userId: String) {
        // TODO: Implement current user storage (SharedPreferences or similar)
    }
    
    override suspend fun searchUsersByName(query: String): List<User> {
        // Используем getAllUsers и фильтруем локально, пока не добавим метод в DAO
        val allUsers = userDao.getAllUsers()
        return allUsers.first()
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { it.toDomain() }
    }
    
    override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {
        userDao.updateUserPoints(userId, points, level)
    }
}
