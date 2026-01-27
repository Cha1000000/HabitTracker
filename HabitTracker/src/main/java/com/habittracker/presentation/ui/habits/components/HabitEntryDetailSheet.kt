package com.habittracker.presentation.ui.habits.components

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.habittracker.R
import com.habittracker.domain.model.HabitEntry
import com.habittracker.presentation.ui.theme.GlassBlack10
import com.habittracker.presentation.ui.theme.GlassWhite10
import com.habittracker.presentation.ui.theme.TahoeDeepBlue
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.ui.theme.TahoeLightBackground
import com.habittracker.presentation.ui.theme.TahoeSkyBlue
import kotlinx.datetime.LocalDate
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * BottomSheet для отображения деталей дня и управления фото-подтверждением.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEntryDetailSheet(
    entry: HabitEntry?,
    date: LocalDate,
    habitTitle: String,
    habitUnit: String?,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onDeletePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    // Launcher для выбора фото из галереи
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAddPhoto(it) }
    }
    
    // Launcher для съёмки фото камерой (TakePicture с Uri)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { uri -> onAddPhoto(uri) }
        }
        tempCameraUri = null
    }
    
    // Launcher для запроса разрешения камеры
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Разрешение получено, запускаем камеру
            val uri = createTempImageUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDarkTheme) TahoeDeepBlue else TahoeLightBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isDarkTheme) GlassWhite10 else GlassBlack10)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Заголовок с датой
            Text(
                text = formatDate(date),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = habitTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Статус выполнения
            StatusBadge(
                entry = entry
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Детали (время, значение, заметка)
            if (entry != null) {
                EntryDetails(
                    entry = entry,
                    habitUnit = habitUnit
                )
                
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            // Секция фото-подтверждения (только для выполненных)
            if (entry?.completed == true) {
                PhotoProofSection(
                    photoUrl = entry.photoUrl,
                    isUploading = isUploading,
                    isDarkTheme = isDarkTheme,
                    onAddClick = { showPhotoSourceDialog = true },
                    onDeleteClick = { showDeleteConfirmDialog = true }
                )
            }
        }
    }
    
    // Диалог выбора источника фото
    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            onDismiss = { showPhotoSourceDialog = false },
            onCameraClick = {
                showPhotoSourceDialog = false
                // Запрашиваем разрешение на камеру
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onGalleryClick = {
                showPhotoSourceDialog = false
                galleryLauncher.launch("image/*")
            },
            isDarkTheme = isDarkTheme
        )
    }
    
    // Диалог подтверждения удаления
    if (showDeleteConfirmDialog) {
        GlassmorphicDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            isDarkTheme = isDarkTheme,
            title = {
                Text(
                    text = stringResource(R.string.photo_proof_delete),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            content = {
                Text(
                    text = stringResource(R.string.photo_proof_delete_confirm),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassmorphicOutlinedButton(
                        onClick = { showDeleteConfirmDialog = false },
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    GlassmorphicButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDeletePhoto()
                        },
                        isDarkTheme = isDarkTheme,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        )
    }
}

@Composable
private fun StatusBadge(entry: HabitEntry?) {
    val (statusText, statusColor) = when {
        entry?.isRestDay == true -> stringResource(R.string.photo_proof_status_rest) to Color(0xFFFFB74D)
        entry?.completed == true -> stringResource(R.string.photo_proof_status_completed) to Color(0xFF4CAF50)
        else -> stringResource(R.string.photo_proof_status_missed) to Color(0xFFEF5350)
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = statusColor
        )
    }
}

@Composable
private fun EntryDetails(entry: HabitEntry, habitUnit: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Время выполнения
        entry.completedAt?.let { completedAt ->
            val timeStr = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                completedAt.hour,
                completedAt.minute
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    TahoeIcons.Clock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.photo_proof_completed_at, timeStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Значение (для измеряемых привычек)
        if (entry.value > 0 && !habitUnit.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    TahoeIcons.Target,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.photo_proof_value, entry.value, habitUnit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Заметка
        entry.note?.takeIf { it.isNotBlank() }?.let { note ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    TahoeIcons.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.photo_proof_note, note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PhotoProofSection(
    photoUrl: String?,
    isUploading: Boolean,
    isDarkTheme: Boolean,
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.photo_proof_add),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (photoUrl != null) {
            // Показываем фото с кнопками изменить/удалить
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = stringResource(R.string.photo_proof_add),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // Кнопки действий
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Изменить
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            TahoeIcons.Edit,
                            contentDescription = stringResource(R.string.photo_proof_change),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Удалить
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            TahoeIcons.Delete,
                            contentDescription = stringResource(R.string.photo_proof_delete),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // Индикатор загрузки
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.photo_proof_uploading),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        } else {
            // Плейсхолдер для добавления фото
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isDarkTheme) {
                                listOf(GlassWhite10, GlassWhite10.copy(alpha = 0.05f))
                            } else {
                                listOf(GlassBlack10, GlassBlack10.copy(alpha = 0.05f))
                            }
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = if (isDarkTheme) GlassWhite10 else GlassBlack10,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !isUploading) { onAddClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isUploading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.photo_proof_uploading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            TahoeIcons.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.photo_proof_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSourceDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    isDarkTheme: Boolean
) {
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        isDarkTheme = isDarkTheme,
        title = {
            Text(
                text = stringResource(R.string.photo_proof_select_source),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Камера
                GlassmorphicButton(
                    onClick = onCameraClick,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        TahoeIcons.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_proof_camera))
                }
                
                // Галерея
                GlassmorphicButton(
                    onClick = onGalleryClick,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        TahoeIcons.Image,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_proof_gallery))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                GlassmorphicOutlinedButton(
                    onClick = onDismiss,
                    isDarkTheme = isDarkTheme,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

private fun formatDate(date: LocalDate): String {
    val javaDate = java.time.LocalDate.of(date.year, date.monthNumber, date.dayOfMonth)
    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    return javaDate.format(formatter)
}

/**
 * Создаёт временный Uri для сохранения фото с камеры
 */
private fun createTempImageUri(context: Context): Uri {
    val tempFile = File.createTempFile(
        "camera_photo_${System.currentTimeMillis()}",
        ".jpg",
        context.cacheDir
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        tempFile
    )
}
