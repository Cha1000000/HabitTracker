package com.habittracker.presentation.ui.habits

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitEntry
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitInvitation
import com.habittracker.domain.model.User
import com.habittracker.presentation.ui.components.SectionCard
import com.habittracker.presentation.ui.components.UserAvatar
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicDialog
import com.habittracker.presentation.ui.habits.components.GlassmorphicDropdownMenu
import com.habittracker.presentation.ui.habits.components.GlassmorphicDropdownMenuItem
import com.habittracker.presentation.ui.habits.components.GradientProgressBar
import com.habittracker.presentation.ui.habits.components.HabitEntryDetailSheet
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.utils.DateTimeFormatter
import com.habittracker.presentation.viewmodel.GroupHabitDetailViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn

/**
 * Экран детальной информации о групповой привычке
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupHabitDetailScreen(
    habitId: String,
    navController: NavController,
    viewModel: GroupHabitDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit = { navController.popBackStack() }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showResetMineDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    
    // State for full screen photo viewer
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }
    
    // Загрузка данных привычки
    LaunchedEffect(habitId) {
        viewModel.loadHabitDetails(habitId)
    }
    
    // Обработка сообщений
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccessMessage()
        }
    }
    
    // SnackBar с предложением добавить фото после выполнения привычки
    val addPhotoText = stringResource(R.string.photo_proof_habit_completed)
    val addPhotoActionText = stringResource(R.string.photo_proof_snackbar_action)
    LaunchedEffect(uiState.showPhotoProofPrompt) {
        if (uiState.showPhotoProofPrompt) {
            val result = snackbarHostState.showSnackbar(
                message = addPhotoText,
                actionLabel = addPhotoActionText,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.showPhotoSheet()
            }
            viewModel.dismissPhotoProofPrompt()
        }
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.habit?.title
                                ?: stringResource(R.string.group_habit_details),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        // Кнопка статистики
                        if (uiState.habit != null) {
                            IconButton(
                                onClick = {
                                    navController.navigate("group_habit_stat_screen/$habitId?isGroup=true")
                                }
                            ) {
                                Icon(
                                    imageVector = TahoeIcons.ChartBar,
                                    contentDescription = stringResource(R.string.cd_habit_statistics)
                                )
                            }
                        }
                        // Меню действий
                        if (uiState.isOwner) {
                            IconButton(
                                onClick = { viewModel.toggleManageMode() }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isManageMode) TahoeIcons.Check else TahoeIcons.Cog,
                                    contentDescription = stringResource(R.string.manage_group)
                                )
                            }
                        }
                        // Overflow menu
                        if (uiState.habit != null) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = TahoeIcons.EllipsisVertical,
                                        contentDescription = stringResource(R.string.reset_menu_title)
                                    )
                                }
                                GlassmorphicDropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    GlassmorphicDropdownMenuItem(
                                        text = { Text(stringResource(R.string.reset_menu_mine)) },
                                        onClick = {
                                            showMenu = false
                                            showResetMineDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = TahoeIcons.Broom,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                    if (uiState.isOwner) {
                                        GlassmorphicDropdownMenuItem(
                                            text = { Text(stringResource(R.string.reset_menu_all)) },
                                            onClick = {
                                                showMenu = false
                                                showResetAllDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = TahoeIcons.Warning,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            if (uiState.isLoading && uiState.habit == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val hasPhotoProofAlready = uiState.currentUserPhotoUrl != null &&
                            uiState.currentUserPhotoUrl!!.isNotEmpty()
                    item {
                        HabitInfoSection(
                            habit = uiState.habit,
                            modifier = Modifier.fillMaxWidth(),
                            isCompletedToday = uiState.isCompletedToday,
                            isMarkingCompletion = uiState.isMarkingCompletion,
                            hasPhotoProof = hasPhotoProofAlready,
                            currentUserPhotoUrl = uiState.currentUserPhotoUrl,
                            currentUserNote = uiState.currentUserNote,
                            isSavingNote = uiState.isUploadingPhoto,
                            onAddPhotoClick = { viewModel.showPhotoSheet() },
                            onPhotoClick = { url -> fullScreenPhotoUrl = url },
                            onSaveNote = { note -> viewModel.updateHabitEntryNote(note) },
                            onToggleCompletion = { viewModel.toggleHabitCompletion() },
                        )
                    }

                    item {
                        GroupProgressSection(
                            today = today,
                            participants = uiState.participants,
                            habitEntries = uiState.habitEntries,
                            currentStreak = uiState.groupStreak,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        ParticipantsSection(
                            today = today,
                            participants = uiState.participants,
                            habitEntries = uiState.habitEntries,
                            isManageMode = uiState.isManageMode,
                            onRemoveParticipant = { userId ->
                                viewModel.removeParticipant(userId)
                            },
                            currentUserId = uiState.currentUserId,
                            nudgedUserIds = uiState.nudgedUserIds,
                            isSendingNudge = uiState.isSendingNudge,
                            onNudge = { userId -> viewModel.sendNudge(userId) },
                            onPhotoClick = { url -> fullScreenPhotoUrl = url },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    if (uiState.participantLeaderboard.isNotEmpty()) {
                        item {
                            ParticipantLeaderboardSection(
                                leaderboard = uiState.participantLeaderboard,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (uiState.isOwner) {
                        item {
                            InviteManagementSection(
                                habit = uiState.habit,
                                inviteCode = uiState.inviteCode,
                                pendingInvitations = uiState.pendingInvitations,
                                isGeneratingCode = uiState.isGeneratingCode,
                                onGenerateCode = { viewModel.generateInviteCode() },
                                onShareCode = { code -> viewModel.shareInviteCode(code) },
                                onDisableCode = { viewModel.disableInviteCode() },
                                onTogglePublic = { isPublic -> viewModel.togglePublicAccess(isPublic) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    item {
                        ActivityHistorySection(
                            habitEntries = uiState.habitEntries,
                            participants = uiState.participants,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!uiState.isOwner) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            LeaveGroupSection(
                                onLeaveGroup = { 
                                    viewModel.leaveGroup {
                                        navController.popBackStack()
                                    }
                                },
                                isLoading = uiState.isLoading,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetMineDialog) {
        GlassmorphicDialog(
            onDismissRequest = { showResetMineDialog = false },
            title = { Text(text = stringResource(R.string.reset_group_mine_title)) },
            content = { Text(text = stringResource(R.string.reset_group_mine_message)) },
            actions = {
                TextButton(onClick = { showResetMineDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                GlassmorphicButton(
                    onClick = {
                        showResetMineDialog = false
                        viewModel.resetMyStats()
                    }
                ) {
                    Text(text = stringResource(R.string.reset_group_mine_action))
                }
            }
        )
    }

    if (showResetAllDialog) {
        GlassmorphicDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text(text = stringResource(R.string.reset_group_all_title)) },
            content = { Text(text = stringResource(R.string.reset_group_all_message)) },
            actions = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                GlassmorphicButton(
                    onClick = {
                        showResetAllDialog = false
                        viewModel.resetForAll()
                    }
                ) {
                    Text(text = stringResource(R.string.reset_group_all_action))
                }
            }
        )
    }
    
    if (uiState.showPhotoSheet && uiState.habit != null) {
        HabitEntryDetailSheet(
            entry = HabitEntry(
                habitId = uiState.habit?.id ?: "",
                userId = uiState.currentUserId,
                date = today,
                completed = uiState.isCompletedToday,
                value = 0,
                note = uiState.currentUserNote,
                photoUrl = uiState.currentUserPhotoUrl,
            ),
            date = today,
            habitTitle = uiState.habit?.title ?: "",
            habitUnit = uiState.habit?.unit,
            isUploading = uiState.isUploadingPhoto,
            successMessage = uiState.photoUploadSuccessMessage,
            onDismiss = { viewModel.hidePhotoSheet() },
            onAddPhoto = { uri -> viewModel.attachPhoto(uri) },
            onDeletePhoto = { viewModel.deletePhoto() },
            onSaveComment = { note -> viewModel.updateHabitEntryNote(note) },
            onSuccessMessageShown = { viewModel.clearPhotoUploadSuccessMessage() }
        )
    }

    fullScreenPhotoUrl?.let { url ->
        FullScreenPhotoDialog(url = url, onDismiss = { fullScreenPhotoUrl = null })
    }
}

@Composable
private fun FullScreenPhotoDialog(url: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableStateOf(1f) }
    var offsetX by remember(url) { mutableStateOf(0f) }
    var offsetY by remember(url) { mutableStateOf(0f) }

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

/**
 * Секция с основной информацией о привычке
 */
@Composable
private fun HabitInfoSection(
    habit: Habit?,
    modifier: Modifier = Modifier,
    isCompletedToday: Boolean = false,
    isMarkingCompletion: Boolean = false,
    onToggleCompletion: () -> Unit = {},
    hasPhotoProof: Boolean = false,
    currentUserPhotoUrl: String? = null,
    currentUserNote: String? = null,
    isSavingNote: Boolean = false,
    onAddPhotoClick: () -> Unit = {},
    onPhotoClick: (String) -> Unit = {},
    onSaveNote: (String) -> Unit = {},
) {
    habit?.let { h ->
        val focusManager = LocalFocusManager.current
        var noteText by remember(h.id) { mutableStateOf(currentUserNote.orEmpty()) }
        var isNoteDirty by remember(h.id) { mutableStateOf(false) }
        val initialNote = currentUserNote.orEmpty()

        LaunchedEffect(h.id, currentUserNote) {
            if (!isNoteDirty) {
                noteText = currentUserNote.orEmpty()
            }
        }

        SectionCard(
            modifier = modifier,
            outlined = false
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            color = try {
                                Color(h.color.toColorInt()).copy(alpha = 0.1f)
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = h.icon,
                        style = MaterialTheme.typography.headlineMedium,
                        color = try {
                            Color(h.color.toColorInt())
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = h.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (h.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = h.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = when (h.frequency) {
                                        HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
                                        HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly) 
                                        HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
                                        HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
                                        HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
                                        HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = TahoeIcons.TrendingUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        
                        if (h.targetValue > 0) {
                            AssistChip(
                                onClick = { },
                                label = {
                                    Text(
                                        text = "${h.targetValue} ${h.unit}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = onToggleCompletion,
                    enabled = !isMarkingCompletion,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCompletedToday) {
                                Color(0xFF4CAF50).copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                ) {
                    if (isMarkingCompletion) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = TahoeIcons.Check,
                            contentDescription = if (isCompletedToday) {
                                stringResource(R.string.habits_unmark_complete)
                            } else {
                                stringResource(R.string.habits_mark_complete)
                            },
                            tint = if (isCompletedToday) {
                                Color(0xFF4CAF50)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            if (isCompletedToday) {
                if (hasPhotoProof && !currentUserPhotoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPhotoClick(currentUserPhotoUrl) }
                    ) {
                        AsyncImage(
                            model = currentUserPhotoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TahoeIcons.MagnifyingGlass,
                                contentDescription = stringResource(R.string.photo_proof_title),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { value ->
                        noteText = value
                        isNoteDirty = value != initialNote
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(text = stringResource(R.string.photo_proof_note_placeholder))
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    maxLines = 4,
                    singleLine = false,
                    trailingIcon = {
                        if (isNoteDirty) {
                            IconButton(
                                onClick = {
                                    onSaveNote(noteText.trim())
                                    isNoteDirty = false
                                    focusManager.clearFocus(force = true)
                                },
                                enabled = !isSavingNote
                            ) {
                                Icon(
                                    imageVector = TahoeIcons.Check,
                                    contentDescription = stringResource(R.string.save),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                )

                GlassmorphicButton(
                    onClick = onAddPhotoClick,
                    modifier = Modifier.fillMaxWidth(),
                    isDarkTheme = isSystemInDarkTheme()
                ) {
                    Icon(
                        imageVector = TahoeIcons.CameraAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (hasPhotoProof) R.string.photo_proof_replace_photo else R.string.photo_proof_add_photo
                        )
                    )
                }
            }
        }
    }
}

/**
 * Секция прогресса группы
 */
@Composable
private fun GroupProgressSection(
    today: kotlinx.datetime.LocalDate,
    participants: List<User>,
    habitEntries: List<HabitEntry>,
    currentStreak: Int,
    modifier: Modifier = Modifier
) {
    val todayEntries = habitEntries.filter { it.date == today }
    val completedToday = todayEntries.count { it.completed }
    val totalParticipants = participants.size
    val completionRate = if (totalParticipants > 0) completedToday.toFloat() / totalParticipants else 0f
    
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.group_progress),
        subtitle = stringResource(R.string.group_progress_subtitle),
        outlined = false
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = stringResource(
                        R.string.group_progress_count_format,
                        completedToday,
                        totalParticipants
                    ) + " " + stringResource(R.string.completed_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.percentage_format,
                        (completionRate * 100).toInt()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val barColors = when {
                completionRate >= 0.75f -> listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer
                )
                completionRate >= 0.5f -> listOf(
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.secondaryContainer
                )
                else -> listOf(
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            }
            GradientProgressBar(
                progress = completionRate,
                modifier = Modifier.fillMaxWidth(),
                colors = barColors,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatisticItem(
                value = "$completedToday",
                label = stringResource(R.string.completed),
                icon = TahoeIcons.CheckCircle
            )
            StatisticItem(
                value = "$currentStreak",
                label = stringResource(R.string.streak_days),
                icon = TahoeIcons.TrendingUp
            )
            StatisticItem(
                value = "$totalParticipants",
                label = stringResource(R.string.participants),
                icon = TahoeIcons.Group
            )
        }
    }
}

@Composable
private fun StatisticItem(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Секция участников группы
 */
@Composable
private fun ParticipantsSection(
    today: kotlinx.datetime.LocalDate,
    participants: List<User>,
    habitEntries: List<HabitEntry>,
    isManageMode: Boolean,
    onRemoveParticipant: (String) -> Unit,
    currentUserId: String,
    nudgedUserIds: Set<String>,
    isSendingNudge: Boolean,
    onNudge: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayEntries = habitEntries.filter { it.date == today }
    
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.participants),
        subtitle = if (isManageMode) {
            stringResource(R.string.tap_to_remove)
        } else {
            pluralStringResource(R.plurals.participants_plural, participants.size, participants.size)
        },
        outlined = false
    ) {
        if (participants.isEmpty()) {
            Text(
                text = stringResource(R.string.no_participants),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                participants.forEach { participant ->
                    val entry = todayEntries.find { it.userId == participant.id }
                    val isCompleted = entry?.completed == true
                    val isNudged = nudgedUserIds.contains(participant.id)
                    val isSelf = participant.id == currentUserId
                    ParticipantListItem(
                        participant = participant,
                        entry = entry,
                        isOwner = participant.id == participants.firstOrNull()?.id,
                        isManageMode = isManageMode,
                        onRemove = { onRemoveParticipant(participant.id) },
                        canNudge = !isCompleted && !isNudged && !isSelf,
                        isNudgeDisabled = isNudged || isSendingNudge,
                        onNudge = { onNudge(participant.id) },
                        onPhotoClick = onPhotoClick
                    )
                }
            }
        }
    }
}

/**
 * Элемент списка участника
 */
@Composable
private fun ParticipantListItem(
    participant: User,
    entry: HabitEntry?,
    isOwner: Boolean,
    isManageMode: Boolean,
    onRemove: () -> Unit,
    canNudge: Boolean,
    isNudgeDisabled: Boolean,
    onNudge: () -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompleted = entry?.completed == true
    val photoUrl = entry?.photoUrl
    val note = entry?.note
    val hasPhoto = !photoUrl.isNullOrBlank()
    val hasNote = !note.isNullOrBlank()
    
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .then(
                if (isManageMode) Modifier.clickable { onRemove() }
                else if (hasPhoto) Modifier.clickable { isExpanded = !isExpanded }
                else Modifier
            )
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                UserAvatar(
                    avatarThumbnailUrl = participant.avatarThumbnailUrl,
                    avatarUrl = participant.avatarUrl,
                    name = participant.name,
                    size = 40.dp
                )
            },
            headlineContent = {
                Column {
                    if (isOwner) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.owner),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = participant.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            supportingContent = {
                Column {
                    Text(
                        text = if (isCompleted) {
                            stringResource(R.string.completed_today)
                        } else {
                            stringResource(R.string.not_completed_today)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!hasPhoto && hasNote) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            },
            trailingContent = {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (hasPhoto) {
                            Icon(
                                imageVector = if (isExpanded) TahoeIcons.ChevronDown else TahoeIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Icon(
                            imageVector = if (isCompleted) {
                                TahoeIcons.CheckCircle
                            } else {
                                TahoeIcons.Circle
                            },
                            contentDescription = null,
                            tint = if (isCompleted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        if (isManageMode && !isOwner) {
                            Icon(
                                imageVector = TahoeIcons.UserMinus,
                                contentDescription = stringResource(R.string.remove_participant),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (canNudge && !isManageMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            onClick = onNudge,
                            enabled = !isNudgeDisabled,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isNudgeDisabled) 0.5f else 1f),
                            modifier = Modifier.height(32.dp).offset(x = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(text = "🥾", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.nudge_button_text),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (isNudgeDisabled) 0.5f else 1f)
                                )
                            }
                        }
                    }
                }
            }
        )
        
        if (isExpanded && hasPhoto) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (hasNote) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { photoUrl.let { onPhotoClick(it) } }
                    ) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = stringResource(R.string.photo_proof_add),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = TahoeIcons.MagnifyingGlass,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Секция управления приглашениями
 */
@Composable
private fun InviteManagementSection(
    habit: Habit?,
    inviteCode: String?,
    pendingInvitations: List<HabitInvitation>,
    isGeneratingCode: Boolean,
    onGenerateCode: () -> Unit,
    onShareCode: (String) -> Unit,
    onDisableCode: () -> Unit,
    onTogglePublic: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.invite_management),
        subtitle = if (pendingInvitations.isNotEmpty()) {
            stringResource(R.string.pending_invitations, pendingInvitations.size)
        } else null,
        outlined = false
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TahoeIcons.Globe,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.public_access),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.public_access_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = habit?.isPublic == true,
                onCheckedChange = onTogglePublic
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        
        Column {
            Text(
                text = stringResource(R.string.invite_code),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (inviteCode != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = inviteCode,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            IconButton(onClick = { 
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("invite_code", inviteCode)))
                                }
                            }) {
                                Icon(
                                    imageVector = TahoeIcons.Clipboard,
                                    contentDescription = stringResource(R.string.copy_code),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onShareCode(inviteCode) }) {
                                Icon(
                                    imageVector = TahoeIcons.Share,
                                    contentDescription = stringResource(R.string.share_code),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onDisableCode) {
                                Icon(
                                    imageVector = TahoeIcons.XMark,
                                    contentDescription = stringResource(R.string.disable_code),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onGenerateCode,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGeneratingCode
                ) {
                    if (isGeneratingCode) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = TahoeIcons.UserPlus,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.generate_invite_code))
                }
            }
        }
    }
}

/**
 * Секция истории активности
 */
@Composable
private fun ActivityHistorySection(
    habitEntries: List<HabitEntry>,
    participants: List<User>,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.recent_activity),
        subtitle = stringResource(R.string.recent_activity_description),
        outlined = false,
        action = {
            Icon(
                imageVector = TahoeIcons.ClockHistory,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        if (habitEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = TahoeIcons.ClockHistory,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.no_activity_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                habitEntries
                    .sortedByDescending { it.completedAt }
                    .take(5)
                    .forEach { entry ->
                        val participant = participants.find { it.id == entry.userId }
                        if (participant != null) {
                            ActivityItem(participant = participant, entry = entry)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
            }
        }
    }
}

@Composable
private fun ActivityItem(
    participant: User,
    entry: HabitEntry,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(
            avatarThumbnailUrl = participant.avatarThumbnailUrl,
            avatarUrl = participant.avatarUrl,
            name = participant.name,
            size = 32.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.completed) {
                    stringResource(R.string.participant_completed, participant.name)
                } else {
                    stringResource(R.string.participant_marked_incomplete, participant.name)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            entry.completedAt?.let { completedDateTime ->
                Text(
                    text = DateTimeFormatter.formatRelativeTime(completedDateTime.toInstant(TimeZone.currentSystemDefault())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = if (entry.completed) TahoeIcons.CheckCircle else TahoeIcons.XCircle,
            contentDescription = null,
            tint = if (entry.completed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Секция выхода из группы
 */
@Composable
private fun LeaveGroupSection(
    onLeaveGroup: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    if (showConfirmDialog) {
        GlassmorphicDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(text = stringResource(R.string.leave_group_title)) },
            content = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = TahoeIcons.ArrowPath, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.leave_group_warning), style = MaterialTheme.typography.bodyMedium)
                }
            },
            actions = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(text = stringResource(R.string.cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                GlassmorphicButton(
                    onClick = { onLeaveGroup(); showConfirmDialog = false }
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text = stringResource(R.string.leave_confirm), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
    OutlinedButton(
        onClick = { showConfirmDialog = true },
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.error)
    ) {
        Icon(imageVector = TahoeIcons.ArrowPath, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.leave_group), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Секция рейтинга участников
 */
@Composable
private fun ParticipantLeaderboardSection(
    leaderboard: List<com.habittracker.domain.usecase.gamification.LeaderboardEntry>,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        title = stringResource(R.string.leaderboard_habit_title),
        outlined = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            leaderboard.forEachIndexed { index, entry ->
                LeaderboardParticipantItem(entry = entry)
                if (index < leaderboard.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardParticipantItem(
    entry: com.habittracker.domain.usecase.gamification.LeaderboardEntry
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = "${entry.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (entry.rank) {
                    1 -> Color(0xFFFFD700)
                    2 -> Color(0xFFC0C0C0)
                    3 -> Color(0xFFCD7F32)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.width(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            UserAvatar(
                avatarThumbnailUrl = entry.user.avatarThumbnailUrl,
                avatarUrl = entry.user.avatarUrl,
                name = entry.user.name,
                size = 36.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = entry.user.name.ifBlank { "User" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${entry.weeklyCompletions}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.leaderboard_completions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupHabitDetailScreenPreview() {
    MaterialTheme {
        // Preview
    }
}
