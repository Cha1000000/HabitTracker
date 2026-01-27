package com.habittracker.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.FirebaseStorage
import com.habittracker.domain.repository.ImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseStorage: FirebaseStorage
) : ImageRepository {

    companion object {
        private const val TAG = "ImageRepository"
        private const val MAX_IMAGE_DIMENSION = 1080
        private const val COMPRESSION_QUALITY = 75
        private const val PROOFS_FOLDER = "proofs"
    }

    override suspend fun compressAndSaveLocally(
        sourceUri: Uri,
        habitId: String,
        userId: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext null

            // Декодируем с учётом размера для экономии памяти
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Вычисляем коэффициент сэмплирования
            val sampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)

            // Декодируем с сэмплированием
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val newInputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext null
            
            var bitmap = BitmapFactory.decodeStream(newInputStream, null, decodeOptions)
                ?: return@withContext null
            newInputStream.close()

            // Корректируем ориентацию по EXIF
            bitmap = correctOrientation(sourceUri, bitmap)

            // Масштабируем если всё ещё слишком большое
            bitmap = scaleBitmapIfNeeded(bitmap, MAX_IMAGE_DIMENSION)

            // Создаём директорию для фото
            val proofDir = File(context.filesDir, "$PROOFS_FOLDER/$userId/$habitId")
            if (!proofDir.exists()) {
                proofDir.mkdirs()
            }

            // Сохраняем сжатое изображение
            val fileName = "proof_${System.currentTimeMillis()}.jpg"
            val outputFile = File(proofDir, fileName)
            
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
            }

            bitmap.recycle()

            Timber.tag(TAG).d("Image compressed and saved: ${outputFile.absolutePath}")
            outputFile.toUri()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to compress and save image")
            null
        }
    }

    override suspend fun uploadToCloud(
        localUri: Uri,
        habitId: String,
        userId: String,
        dateString: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${dateString}_${System.currentTimeMillis()}.jpg"
            val storageRef = firebaseStorage.reference
                .child(PROOFS_FOLDER)
                .child(userId)
                .child(habitId)
                .child(fileName)

            // Загружаем файл
            storageRef.putFile(localUri).await()

            // Получаем публичный URL
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            Timber.tag(TAG).d("Image uploaded to cloud: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to upload image to cloud")
            null
        }
    }

    override suspend fun deleteFromCloud(cloudUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val storageRef = firebaseStorage.getReferenceFromUrl(cloudUrl)
            storageRef.delete().await()
            Timber.tag(TAG).d("Image deleted from cloud: $cloudUrl")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete image from cloud")
            false
        }
    }

    override suspend fun deleteLocal(localUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(localUri.path ?: return@withContext false)
            if (file.exists()) {
                val deleted = file.delete()
                Timber.tag(TAG).d("Local image deleted: $deleted, path: ${file.absolutePath}")
                deleted
            } else {
                true // Файл уже не существует
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete local image")
            false
        }
    }

    @Suppress("SameParameterValue")
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > targetHeight || width > targetWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    @Suppress("SameParameterValue")
    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        val scaledBitmap = bitmap.scale(newWidth, newHeight)
        if (scaledBitmap != bitmap) {
            bitmap.recycle()
        }
        return scaledBitmap
    }

    private fun correctOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return bitmap
            
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                else -> return bitmap
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to correct image orientation")
            bitmap
        }
    }
}
