package com.habittracker.presentation.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitFrequency
import com.habittracker.domain.model.HabitType
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.viewmodel.AuthViewModel
import com.habittracker.presentation.viewmodel.HabitWithCompletion
import com.habittracker.presentation.viewmodel.HabitsViewModel
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    viewModel: HabitsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val authViewModel: AuthViewModel = hiltViewModel()
    
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
                title = { Text("Мои привычки") },
                actions = {
                    IconButton(onClick = { 
                        navController.navigate(Screen.Profile.route) 
                    }) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Профиль"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddEditHabit.route) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить привычку")
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
                Text("Пока нет привычек. Начните с новой!")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.habitsWithCompletion) { habitWithCompletion ->
                        HabitItem(
                            habitWithCompletion = habitWithCompletion,
                            onHabitClick = {
                                navController.navigate(Screen.AddEditHabit.route + "?habitId=${habitWithCompletion.habit.id}")
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

@Composable
fun HabitItem(
    habitWithCompletion: HabitWithCompletion,
    onHabitClick: (HabitWithCompletion) -> Unit,
    onToggleCompletion: () -> Unit
) {
    val habit = habitWithCompletion.habit
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHabitClick(habitWithCompletion) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (habitWithCompletion.isCompletedToday) {
                Color(0xFF4CAF50).copy(alpha = 0.1f) // Светло-зеленый фон для выполненных
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка привычки с цветным фоном
            Text(
                text = habit.icon,
                fontSize = 24.sp,
                modifier = Modifier
                    .size(48.dp)
                    .padding(8.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Информация о привычке
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (habitWithCompletion.isCompletedToday) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = habit.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (habitWithCompletion.isCompletedToday) {
                        Color(0xFF4CAF50).copy(alpha = 0.7f)
                    } else {
                        Color.Gray
                    }
                )
                
                // Показываем частоту
                Text(
                    text = when (habit.frequency) {
                        HabitFrequency.DAILY -> "Каждый день"
                        HabitFrequency.WEEKLY -> "Раз в неделю"
                        HabitFrequency.WEEKDAYS -> "По будням"
                        HabitFrequency.WEEKENDS -> "По выходным"
                        HabitFrequency.CUSTOM -> "Произвольно"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Кнопка отметки выполнения
            IconButton(
                onClick = onToggleCompletion,
                enabled = !habitWithCompletion.isLoading
            ) {
                if (habitWithCompletion.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = if (habitWithCompletion.isCompletedToday) {
                            "Снять отметку"
                        } else {
                            "Отметить как выполненную"
                        },
                        tint = if (habitWithCompletion.isCompletedToday) {
                            Color(0xFF4CAF50)
                        } else {
                            Color.Gray
                        }
                    )
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
fun HabitItemPreview() {
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
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now()
    )
    
    Column {
        HabitItem(
            habitWithCompletion = HabitWithCompletion(
                habit = sampleHabit,
                isCompletedToday = false,
                isLoading = false
            ),
            onHabitClick = {},
            onToggleCompletion = {}
        )
        
        Spacer(modifier = Modifier.padding(8.dp))
        
        HabitItem(
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
