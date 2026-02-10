package com.habittracker.presentation.ui.habits.components

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.habittracker.R
import com.habittracker.domain.model.HabitEntry
import com.habittracker.presentation.ui.theme.GlassBlack10
import com.habittracker.presentation.ui.theme.GlassWhite10
import com.habittracker.presentation.ui.theme.TahoeDeepBlue
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.ui.theme.TahoeLightBackground
import com.habittracker.presentation.ui.theme.TahoeSkyBlue
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEntryDetailSheet(
    entry: HabitEntry?,
    date: LocalDate,
    habitTitle: String,
    habitUnit: String?,
    isUploading: Boolean,
    successMessage: String?,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onAddPhoto: (Uri) -> Unit = {},
    onDeletePhoto: () -> Unit = {},
    onSaveComment: ((String) -> Unit)? = null,
    onSuccessMessageShown: () -> Unit,
    isReadOnly: Boolean = false,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    var isPickingPhoto by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            if (newValue == Hidden) {
                !isUploading && !isPickingPhoto
            } else {
                true
            }
        }
    )
    
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var tempCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val tempCameraUri = remember(tempCameraUriString) { tempCameraUriString?.toUri() }
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }

    // Локальное состояние для комментария
    var commentText by remember(entry?.note) { mutableStateOf(entry?.note ?: "") }
    val isCommentModified = commentText != (entry?.note ?: "")

    BackHandler(enabled = isUploading || isPickingPhoto || successMessage != null) {
        // Block back
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            isPickingPhoto = false
        } else {
            onAddPhoto(uri)
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { uri -> onAddPhoto(uri) }
        } else {
            isPickingPhoto = false
        }
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempImageUri(context)
            tempCameraUriString = uri.toString()
            cameraLauncher.launch(uri)
        } else {
            isPickingPhoto = false
        }
    }

    LaunchedEffect(isUploading, successMessage) {
        if (isUploading || successMessage != null) {
            isPickingPhoto = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isUploading && !isPickingPhoto) {
                onDismiss()
            }
        },
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
            if (successMessage != null) {
                LaunchedEffect(successMessage) {
                    delay(1500)
                    onSuccessMessageShown()
                }
                GlassmorphicCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    isDarkTheme = isDarkTheme,
                    cornerRadius = 10.dp,
                    glassAlpha = if (isDarkTheme) 0.2f else 0.85f
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TahoeIcons.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = successMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
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
            
            EntryStatusBadge(entry = entry)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (entry != null) {
                EntryDetails(
                    entry = entry,
                    habitUnit = habitUnit,
                    commentText = commentText,
                    onCommentChange = { commentText = it },
                    isReadOnly = isReadOnly,
                    isModified = isCommentModified,
                    onSave = { onSaveComment?.invoke(commentText) },
                    isDarkTheme = isDarkTheme
                )
                
                Spacer(modifier = Modifier.height(20.dp))
            }
            
            if (entry?.completed == true) {
                PhotoProofSection(
                    photoUrl = entry.photoUrl,
                    isUploading = isUploading,
                    isDarkTheme = isDarkTheme,
                    isReadOnly = isReadOnly,
                    onPhotoClick = { fullScreenPhotoUrl = entry.photoUrl },
                    onAddClick = { showPhotoSourceDialog = true },
                    onDeleteClick = { showDeleteConfirmDialog = true }
                )
            }
        }
    }

    fullScreenPhotoUrl?.let { url ->
        FullScreenImageDialog(url = url, onDismiss = { fullScreenPhotoUrl = null })
    }
    
    if (showPhotoSourceDialog) {
        PhotoSourceDialog(
            onDismiss = { showPhotoSourceDialog = false },
            onCameraClick = {
                isPickingPhoto = true
                showPhotoSourceDialog = false
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onGalleryClick = {
                isPickingPhoto = true
                showPhotoSourceDialog = false
                galleryLauncher.launch("image/*")
            },
            isDarkTheme = isDarkTheme
        )
    }
    
    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                onDeletePhoto()
            },
            isDarkTheme = isDarkTheme
        )
    }
}

@Composable
private fun EntryStatusBadge(entry: HabitEntry?) {
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
private fun EntryDetails(
    entry: HabitEntry,
    habitUnit: String?,
    commentText: String,
    onCommentChange: (String) -> Unit,
    isReadOnly: Boolean,
    isModified: Boolean,
    onSave: () -> Unit,
    isDarkTheme: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        
        if (isReadOnly) {
            if (commentText.isNotBlank()) {
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
                        text = stringResource(R.string.photo_proof_note, commentText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = commentText,
                onValueChange = onCommentChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.photo_proof_note_placeholder),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                trailingIcon = {
                    if (isModified) {
                        IconButton(onClick = onSave) {
                            Icon(
                                imageVector = TahoeIcons.Check,
                                contentDescription = stringResource(R.string.save),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PhotoProofSection(
    photoUrl: String?,
    isUploading: Boolean,
    isDarkTheme: Boolean,
    isReadOnly: Boolean,
    onPhotoClick: () -> Unit,
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
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isUploading) { onPhotoClick() },
                    contentScale = ContentScale.Crop
                )
                
                if (!isReadOnly) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onAddClick,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                TahoeIcons.Edit,
                                contentDescription = stringResource(R.string.photo_proof_change),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                TahoeIcons.Delete,
                                contentDescription = stringResource(R.string.photo_proof_delete),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                
                if (isUploading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
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
            if (!isReadOnly) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (isDarkTheme) listOf(GlassWhite10, GlassWhite10.copy(alpha = 0.05f))
                                else listOf(GlassBlack10, GlassBlack10.copy(alpha = 0.05f))
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
                        CircularProgressIndicator(
                            color = if (isDarkTheme) TahoeSkyBlue else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.photo_proof_no_photo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenImageDialog(url: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offsetX by remember(url) { mutableFloatStateOf(0f) }
    var offsetY by remember(url) { mutableFloatStateOf(0f) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        if (newScale != scale) {
            val scaleRatio = newScale / scale
            offsetX *= scaleRatio
            offsetY *= scaleRatio
        }
        scale = newScale
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(url) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2f
                                }
                            }
                        )
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(state = transformableState),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    TahoeIcons.XMark,
                    contentDescription = stringResource(R.string.action_close),
                    tint = Color.White
                )
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
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassmorphicButton(onClick = onCameraClick, isDarkTheme = isDarkTheme, modifier = Modifier.fillMaxWidth()) {
                    Icon(TahoeIcons.Camera, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_proof_camera))
                }
                GlassmorphicButton(onClick = onGalleryClick, isDarkTheme = isDarkTheme, modifier = Modifier.fillMaxWidth()) {
                    Icon(TahoeIcons.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.photo_proof_gallery))
                }
                Spacer(modifier = Modifier.height(8.dp))
                GlassmorphicOutlinedButton(onClick = onDismiss, isDarkTheme = isDarkTheme, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, isDarkTheme: Boolean) {
    GlassmorphicDialog(
        onDismissRequest = onDismiss,
        isDarkTheme = isDarkTheme,
        title = {
            Text(
                text = stringResource(R.string.photo_proof_delete),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        content = {
            Text(text = stringResource(R.string.photo_proof_delete_confirm), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassmorphicOutlinedButton(onClick = onDismiss, isDarkTheme = isDarkTheme, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                GlassmorphicButton(onClick = onConfirm, isDarkTheme = isDarkTheme, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.delete))
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

private fun createTempImageUri(context: Context): Uri {
    val tempFile = File.createTempFile("camera_photo_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}
