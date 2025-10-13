package com.habittracker.presentation.ui.habits

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.habittracker.HabitTrackerApplication
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.components.GradientPullRefreshIndicator
import com.habittracker.presentation.ui.habits.components.ConfirmDialog
import com.habittracker.presentation.ui.habits.components.EnhancedHabitItem
import com.habittracker.presentation.ui.habits.components.ExpandableFAB
import com.habittracker.presentation.ui.habits.components.JoinByCodeDialog
import com.habittracker.presentation.ui.habits.components.TahoeBackToTodayButton
import com.habittracker.presentation.ui.habits.components.TahoeDateHeader
import com.habittracker.presentation.ui.habits.components.TahoeDateSelector
import com.habittracker.presentation.ui.habits.components.TahoeDockBar
import com.habittracker.presentation.ui.habits.components.TahoeDockDivider
import com.habittracker.presentation.ui.habits.components.TahoeDockItem
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.notifications.NotificationBadge
import com.habittracker.presentation.viewmodel.FriendsViewModel
import com.habittracker.presentation.viewmodel.HabitWithCompletion
import com.habittracker.presentation.viewmodel.HabitsViewModel
import com.habittracker.presentation.viewmodel.NotificationsViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    viewModel: HabitsViewModel = hiltViewModel(),
    notificationsViewModel: NotificationsViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val habitDeletedText = stringResource(id = R.string.habit_deleted)
    val badgeCount by notificationsViewModel.badgeCount.collectAsState()
    val friendsUiState by friendsViewModel.uiState.collectAsState()
    val friendRequestsBadgeCount = friendsUiState.incomingRequests.size
    var isRefreshing by remember { mutableStateOf(false) }
    var recomposeKey by remember { mutableIntStateOf(0) }
    
    // Данные для селектора дат
    val selectedDate = uiState.selectedDate ?: Clock.System.now()
        .toLocalDateTime(currentSystemDefault()).date
    val today = Clock.System.now()
        .toLocalDateTime(currentSystemDefault()).date
    val weekDates = viewModel.getCurrentWeekDates()
    val weekCompletionData = uiState.weekCompletionData
    val isToday = selectedDate == today
    val isCurrentWeekDisplayed = viewModel.isCurrentWeekDisplayed()
    
    // Логирование для отладки
    LaunchedEffect(uiState.selectedDate) {
        Log.d("HabitsScreen", "🎯 selectedDate changed: ${uiState.selectedDate}, computed: $selectedDate, isToday: $isToday")
    }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    val appStateManager = remember {
        (context.applicationContext as? HabitTrackerApplication)?.appStateManager
    }

    // Отслеживаем lifecycle и обновляем состояние экрана
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    appStateManager?.setCurrentScreen("habits_screen")
                    recomposeKey++
                    viewModel.refresh()
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
    
    // Показываем ошибки через Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }
    
    // Оборачиваем весь экран в градиентный фон Tahoe
    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent, // Делаем фон Scaffold прозрачным
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.habits_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                actions = {
                    // Оставляем только колокольчик уведомлений
                    Box {
                        IconButton(onClick = {
                            navController.navigate(Screen.Notifications.route)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.cd_notifications)
                            )
                        }
                        NotificationBadge(
                            count = badgeCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-8).dp, y = 6.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Показываем FAB только для сегодняшнего дня и будущих дат
            if (selectedDate >= today) {
                var showCodeDialog by remember { mutableStateOf(false) }
                
                ExpandableFAB(
                    onCreateHabit = { 
                        // Передаём выбранную дату как параметр при создании привычки
                        val dateParam = "?startDate=$selectedDate"
                        navController.navigate(Screen.AddEditHabit.route + dateParam) 
                    },
                    onBrowsePublic = { 
                        navController.navigate(Screen.PublicHabits.route) 
                    },
                    onJoinByCode = { 
                        showCodeDialog = true 
                    }
                )
                
                // Диалог ввода кода
                if (showCodeDialog) {
                    JoinByCodeDialog(
                        onDismiss = { showCodeDialog = false },
                        onJoin = { code ->
                            showCodeDialog = false
                            viewModel.joinByInviteCode(code)
                        }
                    )
                }
            }
        },
        bottomBar = {
            TahoeDockBar(
                isDarkTheme = isSystemInDarkTheme()
            ) {
                // Статистика
                TahoeDockItem(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = stringResource(R.string.cd_stats),
                    onClick = { navController.navigate(Screen.Stats.route) },
                    selected = false
                )

                TahoeDockDivider()
                
                // Достижения
                TahoeDockItem(
                    icon = Icons.Default.EmojiEvents,
                    label = stringResource(R.string.cd_achievements),
                    onClick = { navController.navigate(Screen.Achievements.route) },
                    selected = false
                )

                TahoeDockDivider()
                
                // Друзья
                TahoeDockItem(
                    icon = Icons.Default.People,
                    label = stringResource(R.string.cd_friends),
                    badge = friendRequestsBadgeCount,
                    onClick = { navController.navigate(Screen.Friends.route) },
                    selected = false
                )

                TahoeDockDivider()
                
                // Профиль
                TahoeDockItem(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.cd_profile),
                    onClick = { navController.navigate(Screen.Profile.route) },
                    selected = false
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.refresh()
            }
        )

        // Подсчёт процента выполнения
        val completionPercentage = if (uiState.habitsWithCompletion.isEmpty()) {
            0
        } else {
            val completed = uiState.habitsWithCompletion.count { it.isCompletedToday }
            (completed * 100) / uiState.habitsWithCompletion.size
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Заголовок с датой и прогрессом
            TahoeDateHeader(
                selectedDate = selectedDate,
                completionPercentage = completionPercentage,
                isToday = isToday,
                isDarkTheme = isSystemInDarkTheme()
            )
            
            // Селектор дат
            key(selectedDate) {
                TahoeDateSelector(
                    selectedDate = selectedDate,
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                    },
                    weekDates = weekDates,
                    completionData = weekCompletionData,
                    isDarkTheme = isSystemInDarkTheme(),
                    onSwipeLeft = { viewModel.goToNextWeek() },
                    onSwipeRight = { viewModel.goToPreviousWeek() },
                    canSwipeLeft = viewModel.canGoToNextWeek(),
                    canSwipeRight = viewModel.canGoToPreviousWeek(),
                    onBoundaryReached = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = message,
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
            }
            
            // Кнопка "Сегодня" (показывается когда отображается не текущая неделя)
            if (!isCurrentWeekDisplayed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TahoeBackToTodayButton(
                        onClick = { viewModel.goToToday() },
                        isDarkTheme = isSystemInDarkTheme()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Основной контент с привычками
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    // Сбрасываем индикатор после прихода новых данных
                    LaunchedEffect(uiState.habitsWithCompletion) {
                        isRefreshing = false
                    }
                    Box(Modifier.pullRefresh(pullRefreshState)) {
                        if (uiState.habitsWithCompletion.isEmpty()) {
                            Text(stringResource(R.string.habits_empty_state))
                        } else {
                            key(recomposeKey) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                items(
                                    uiState.habitsWithCompletion,
                                    key = { hwc -> "${hwc.habit.id}:${hwc.isCompletedToday}" }
                                ) { habitWithCompletion ->
                                    var showDeleteDialog by remember { mutableStateOf(false) }
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                                showDeleteDialog = true
                                            }
                                            // Не позволяем автоматически закрепить состояние, пока не подтвердим
                                            false
                                        }
                                    )

                                    SwipeToDismissBox(
                                        state = dismissState,
                                        modifier = Modifier.testTag("habit-${habitWithCompletion.habit.id}"),
                                        enableDismissFromStartToEnd = false,
                                        backgroundContent = {
                                            // Фон удаления: проявляется только при свайпе, совпадает по скруглению с карточкой
                                            val shape = MaterialTheme.shapes.medium
                                            val offsetPx = try { dismissState.requireOffset() } catch (_: IllegalStateException) { 0f }
                                            val targetAlpha = if (abs(offsetPx) > 0f) {
                                                // Наращиваем непрозрачность с прогрессом свайпа
                                                (abs(offsetPx) / 600f).coerceIn(0f, 1f)
                                            } else 0f
                                            val bgAlpha = animateFloatAsState(targetValue = targetAlpha, label = "swipeBgAlpha").value
                                            val coral = Color(0xFFD74646)

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 4.dp)
                                                    .clip(shape)
                                                    .background(coral.copy(alpha = bgAlpha)),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = MaterialTheme.colorScheme.onError.copy(alpha = bgAlpha),
                                                    modifier = Modifier.padding(end = 24.dp)
                                                )
                                            }
                                        }
                                    ) {
                                        key(recomposeKey) {
                                            EnhancedHabitItem(
                                                habitWithCompletion = habitWithCompletion,
                                                onHabitClick = { habit ->
                                                    if (habit.habit.isGroupHabit()) {
                                                        navController.navigate(Screen.GroupHabitDetail.createRoute(habit.habit.id))
                                                    } else {
                                                        navController.navigate(Screen.AddEditHabit.route + "?habitId=${habit.habit.id}")
                                                    }
                                                },
                                                onToggleCompletion = {
                                                    viewModel.toggleHabitCompletion(habitWithCompletion.habit.id)
                                                },
                                                onEdit = { habit ->
                                                    navController.navigate(Screen.AddEditHabit.route + "?habitId=${habit.habit.id}")
                                                },
                                                onDelete = {
                                                    showDeleteDialog = true
                                                }
                                            )
                                        }
                                    }

                                    if (showDeleteDialog) {
                                        ConfirmDialog(
                                            title = stringResource(id = R.string.confirm_delete_habit_title),
                                            message = stringResource(id = R.string.confirm_delete_habit_message, habitWithCompletion.habit.title),
                                            confirmLabel = stringResource(id = R.string.delete),
                                            cancelLabel = stringResource(id = R.string.cancel),
                                            onConfirm = {
                                                showDeleteDialog = false
                                                // Мгновенно удаляем (локально + Firestore) без UNDO, чтобы элемент не возвращался при refresh
                                                viewModel.deleteHabit(habitWithCompletion.habit.id)
                                                // Показываем короткое уведомление об успехе
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = habitDeletedText,
                                                        withDismissAction = true,
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            },
                                            onDismiss = { showDeleteDialog = false }
                                        )
                                    }
                                }
                                }
                            }
                        }
                        GradientPullRefreshIndicator(
                            refreshing = isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(1f)
                        )
                    }
                }
            }
        }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HabitsScreenPreview() {
    HabitsScreen(navController = rememberNavController())
}

@Preview(showBackground = true)
@Composable
fun EnhancedHabitItemPreview() {
    val sampleHabit = Habit(
        id = "1",
        title = "Бег по утрам",
        description = "Пробежка в парке 30 минут",
        icon = "🏃",
        color = "#2196F3",
        type = HabitType.PERSONAL,
        frequency = HabitFrequency.DAILY,
        reminderTime = null,
        reminderEnabled = false,
        targetValue = 1,
        unit = "раз",
        createdBy = "user1",
        participants = listOf("user1"),
        isActive = true,
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
    
    Column {
        EnhancedHabitItem(
            habitWithCompletion = HabitWithCompletion(
                habit = sampleHabit,
                isCompletedToday = false,
                isLoading = false
            ),
            onHabitClick = {},
            onToggleCompletion = {}
        )
        
        Spacer(modifier = Modifier.padding(8.dp))
        
        EnhancedHabitItem(
            habitWithCompletion = HabitWithCompletion(
                habit = sampleHabit.copy(title = "Чтение книги", icon = "📚"),
                isCompletedToday = true,
                isLoading = false
            ),
            onHabitClick = {},
            onToggleCompletion = {}
        )
    }
}
