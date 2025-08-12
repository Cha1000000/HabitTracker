package com.habittracker.data.repository

import com.habittracker.data.local.dao.UserDao
import com.habittracker.domain.model.User
import com.habittracker.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.habittracker.data.local.mappers.toDomain as userToDomain
import com.habittracker.data.local.mappers.toEntity as userToEntity

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {
    
    override suspend fun insertUser(user: User) {
        userDao.insertUser(user.userToEntity())
    }
    
    override suspend fun updateUser(user: User) {
        userDao.updateUser(user.userToEntity())
    }
    
    override suspend fun deleteUser(userId: String) {
        val user = userDao.getUserById(userId)
        if (user != null) {
            userDao.deleteUser(user)
        }
    }
    
    override suspend fun getUserById(userId: String): User? {
        return userDao.getUserById(userId)?.userToDomain()
    }
    
    override suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)?.userToDomain()
    }
    
    override fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { entities ->
            entities.map { it.userToDomain() }
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
            .map { it.userToDomain() }
    }
    
    override suspend fun updateUserLevel(userId: String, level: Int, points: Int) {
        userDao.updateUserPoints(userId, points, level)
    }
}
