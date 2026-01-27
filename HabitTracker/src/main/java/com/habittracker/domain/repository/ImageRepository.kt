package com.habittracker.domain.repository

import android.net.Uri

/**
 * Репозиторий для работы с изображениями (фото-подтверждения).
 * Отвечает за сжатие, локальное сохранение и загрузку в облако.
 */
interface ImageRepository {
    
    /**
     * Сжимает изображение и сохраняет локально.
     * @param sourceUri URI исходного изображения (из камеры или галереи)
     * @param habitId ID привычки (для организации папок)
     * @param userId ID пользователя
     * @return URI сжатого локального файла или null при ошибке
     */
    suspend fun compressAndSaveLocally(
        sourceUri: Uri,
        habitId: String,
        userId: String
    ): Uri?
    
    /**
     * Загружает изображение в Firebase Storage.
     * @param localUri URI локального файла
     * @param habitId ID привычки
     * @param userId ID пользователя
     * @param dateString Дата выполнения (для уникального имени файла)
     * @return URL загруженного файла в облаке или null при ошибке
     */
    suspend fun uploadToCloud(
        localUri: Uri,
        habitId: String,
        userId: String,
        dateString: String
    ): String?
    
    /**
     * Удаляет изображение из Firebase Storage.
     * @param cloudUrl URL файла в облаке
     * @return true если удаление успешно
     */
    suspend fun deleteFromCloud(cloudUrl: String): Boolean
    
    /**
     * Удаляет локальный файл изображения.
     * @param localUri URI локального файла
     * @return true если удаление успешно
     */
    suspend fun deleteLocal(localUri: Uri): Boolean
}
