package com.habittracker.domain.usecase.habit

import android.net.Uri
import com.habittracker.domain.repository.HabitEntryRepository
import com.habittracker.domain.repository.ImageRepository
import kotlinx.datetime.LocalDate
import timber.log.Timber
import javax.inject.Inject

/**
 * UseCase для прикрепления фото-подтверждения к записи о выполнении привычки.
 * 
 * Процесс:
 * 1. Сжимает изображение и сохраняет локально
 * 2. Загружает в Firebase Storage
 * 3. Обновляет запись HabitEntry с URL фото
 */
class AttachPhotoToEntryUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val habitEntryRepository: HabitEntryRepository
) {
    
    suspend operator fun invoke(
        sourceUri: Uri,
        habitId: String,
        userId: String,
        date: LocalDate
    ): Result<String> {
        return try {
            Timber.d("AttachPhotoToEntryUseCase: Starting for habitId=$habitId, date=$date")
            
            // 1. Сжимаем и сохраняем локально
            val localUri = imageRepository.compressAndSaveLocally(sourceUri, habitId, userId)
                ?: return Result.failure(Exception("Failed to compress image"))
            
            Timber.d("AttachPhotoToEntryUseCase: Image compressed locally: $localUri")
            
            // 2. Загружаем в облако
            val cloudUrl = imageRepository.uploadToCloud(
                localUri = localUri,
                habitId = habitId,
                userId = userId,
                dateString = date.toString()
            ) ?: return Result.failure(Exception("Failed to upload image to cloud"))
            
            Timber.d("AttachPhotoToEntryUseCase: Image uploaded to cloud: $cloudUrl")
            
            // 3. Обновляем запись в БД
            habitEntryRepository.updatePhotoUrl(habitId, userId, date, cloudUrl)
            
            Timber.d("AttachPhotoToEntryUseCase: Entry updated with photo URL")
            
            // 4. Удаляем локальный файл (он больше не нужен)
            imageRepository.deleteLocal(localUri)
            
            Result.success(cloudUrl)
        } catch (e: Exception) {
            Timber.e(e, "AttachPhotoToEntryUseCase: Failed to attach photo")
            Result.failure(e)
        }
    }
}
