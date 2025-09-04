package com.habittracker.presentation.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.domain.model.isGroupHabit
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.habits.components.EnhancedHabitItem
import com.habittracker.presentation.ui.notifications.NotificationBadge
import com.habittracker.presentation.viewmodel.HabitWithCompletion
import com.habittracker.presentation.viewmodel.HabitsViewModel
import com.habittracker.presentation.viewmodel.NotificationsViewModel
import com.habittracker.presentation.viewmodel.FriendsViewModel
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    viewModel: HabitsViewModel = hiltViewModel(),
    notificationsViewModel: NotificationsViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val badgeCount by notificationsViewModel.badgeCount.collectAsState()
    val friendsUiState by friendsViewModel.uiState.collectAsState()
    val friendRequestsBadgeCount = friendsUiState.incomingRequests.size
    
    // Показываем ошибки через Snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habits_title)) },
                actions = {
                    // Кнопки геймификации
                    IconButton(onClick = { 
                        navController.navigate(Screen.Stats.route) 
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = stringResource(R.string.cd_stats)
                        )
                    }
                    IconButton(onClick = { 
                        navController.navigate(Screen.Achievements.route) 
                    }) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = stringResource(R.string.cd_achievements)
                        )
                    }
                    
                    // Существующие кнопки
                    IconButton(onClick = { 
                        navController.navigate(Screen.Profile.route) 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(R.string.cd_profile)
                        )
                    }
                    Box {
                        IconButton(onClick = { 
                            navController.navigate(Screen.Friends.route) 
                        }) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = stringResource(R.string.cd_friends)
                            )
                        }
                        NotificationBadge(
                            count = friendRequestsBadgeCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-8).dp, y = 6.dp)
                        )
                    }
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
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddEditHabit.route) }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_habit))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.habitsWithCompletion.isEmpty()) {
                Text(stringResource(R.string.habits_empty_state))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.habitsWithCompletion) { habitWithCompletion ->
                        EnhancedHabitItem(
                            habitWithCompletion = habitWithCompletion,
                            onHabitClick = { habit ->
                                if (habit.habit.isGroupHabit()) {
                                    // Для групповых привычек переходим к детальному экрану
                                    navController.navigate(Screen.GroupHabitDetail.createRoute(habit.habit.id))
                                } else {
                                    // Для обычных привычек переходим к редактированию
                                    navController.navigate(Screen.AddEditHabit.route + "?habitId=${habit.habit.id}")
                                }
                            },
                            onToggleCompletion = {
                                viewModel.toggleHabitCompletion(habitWithCompletion.habit.id)
                            }
                        )
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
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds()
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
