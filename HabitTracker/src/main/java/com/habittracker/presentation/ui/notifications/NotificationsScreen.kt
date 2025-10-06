package com.habittracker.presentation.ui.notifications

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.habittracker.HabitTrackerApplication
import com.habittracker.R
import com.habittracker.domain.usecase.notification.GroupHabitNotification
import com.habittracker.domain.usecase.notification.NotificationType
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.NotificationsViewModel
import kotlinx.datetime.Clock

/**
 * Экран уведомлений групповых привычек
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = hiltViewModel(),
    onBackClick: () -> Unit = { navController.popBackStack() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appStateManager = remember {
        (context.applicationContext as? HabitTrackerApplication)?.appStateManager
    }

    // Отслеживаем lifecycle и обновляем состояние экрана
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    appStateManager?.setCurrentScreen("notifications_screen")
                    viewModel.refreshNotifications()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    appStateManager?.setCurrentScreen(null)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            appStateManager?.setCurrentScreen(null)
        }
    }
    
    // Обработка сообщений об ошибках
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.onErrorShown()
        }
    }
    // Ошибки по ресурсам (общие)
    LaunchedEffect(uiState.errorResId) {
        uiState.errorResId?.let { resId ->
            snackbarHostState.showSnackbar(
                message = context.getString(resId),
                duration = SnackbarDuration.Long
            )
            viewModel.onErrorShown()
        }
    }
    // Ошибки секции приглашений
    LaunchedEffect(uiState.invitesError) {
        uiState.invitesError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.onErrorShown()
        }
    }
    // Ошибки секции приглашений по ресурсам
    LaunchedEffect(uiState.invitesErrorResId) {
        uiState.invitesErrorResId?.let { resId ->
            snackbarHostState.showSnackbar(
                message = context.getString(resId),
                duration = SnackbarDuration.Long
            )
            viewModel.onErrorShown()
        }
    }
    
    // Обработка успешных действий
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.onSuccessMessageShown()
        }
    }
    // Успехи по ресурсам
    LaunchedEffect(uiState.successResId) {
        uiState.successResId?.let { resId ->
            snackbarHostState.showSnackbar(
                message = context.getString(resId),
                duration = SnackbarDuration.Short
            )
            viewModel.onSuccessMessageShown()
        }
    }
    
    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                NotificationsTopBar(
                    unreadCount = uiState.unreadCount,
                    onBackClick = onBackClick,
                    onMarkAllAsRead = { viewModel.markAllAsRead() },
                    isDarkTheme = isDarkTheme
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Секция приглашений к привычкам
            InvitationsSection(
                invitations = uiState.invitations,
                isLoading = uiState.isInvitesLoading,
                processingIds = uiState.processingInvitationIds,
                onAccept = { viewModel.acceptInvitation(it) },
                onDecline = { viewModel.declineInvitation(it) }
            )

            // Разделитель
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Список уведомлений
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.notifications.isEmpty() && uiState.storedNotifications.isEmpty() -> {
                        EmptyNotificationsState()
                    }
                    else -> {
                        // Отображаем сохраненные уведомления из базы данных
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = uiState.storedNotifications,
                                key = { "${it.id}_${it.isRead}" }
                            ) { notification ->
                                StoredNotificationItem(
                                    notification = notification,
                                    onClick = {
                                        // Отмечаем как прочитанное
                                        if (!notification.isRead) {
                                            viewModel.markNotificationAsRead(notification.id)
                                        }
                                        // Навигация к деталям групповой привычки
                                        navController.navigate(Screen.GroupHabitDetail.createRoute(notification.habitId))
                                    },
                                    onDelete = {
                                        viewModel.deleteNotification(notification.id)
                                    }
                                )
                            }
                            
                            // Старые уведомления (из GroupHabitNotificationUseCase)
                            items(
                                items = uiState.notifications,
                                key = { it.id }
                            ) { notification ->
                                NotificationItem(
                                    notification = notification,
                                    onClick = { viewModel.onNotificationClick(notification) },
                                    onMarkAsRead = { viewModel.markAsRead(notification.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * Верхняя панель экрана уведомлений
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTopBar(
    unreadCount: Int,
    onBackClick: () -> Unit,
    onMarkAllAsRead: () -> Unit,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.notifications_title))
                if (unreadCount > 0) {
                    NotificationBadge(count = unreadCount)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        actions = {
            if (unreadCount > 0) {
                IconButton(onClick = onMarkAllAsRead) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = stringResource(R.string.mark_all_as_read)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
            titleContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground
        ),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun NotificationsScreenPreview() {
    MaterialTheme {
        // Создаем mock данные для превью
        val mockNotifications = listOf(
            GroupHabitNotification(
                id = "1",
                type = NotificationType.PARTICIPANT_COMPLETED,
                habitId = "habit1",
                habitTitle = "Утренняя зарядка",
                participantName = "Анна",
                message = "Анна выполнила привычку \"Утренняя зарядка\"",
                timestamp = Clock.System.now(),
                isRead = false
            ),
            GroupHabitNotification(
                id = "2",
                type = NotificationType.GROUP_ACHIEVEMENT,
                habitId = "habit2",
                habitTitle = "Чтение книг",
                message = "Все участники выполнили \"Чтение книг\" сегодня! 🎉",
                timestamp = Clock.System.now(),
                isRead = true
            )
        )
        
        Column {
            NotificationsTopBar(
                unreadCount = 3,
                onBackClick = {},
                onMarkAllAsRead = {}
            )
            NotificationsList(
                notifications = mockNotifications,
                onNotificationClick = {},
                onMarkAsRead = {}
            )
        }
    }
}
