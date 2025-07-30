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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.habittracker.presentation.viewmodel.HabitsViewModel
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    navController: NavController,
    viewModel: HabitsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val authViewModel: AuthViewModel = hiltViewModel()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои привычки") },
                actions = {
                    IconButton(onClick = { 
                        navController.navigate(Screen.Profile.route) 
                    }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Профиль"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                navController.navigate(Screen.AddEditHabit.route)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Добавить привычку")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.habits.isEmpty()) {
                Text("Пока нет привычек. Начните с новой!")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.habits) { habit ->
                        HabitItem(habit = habit, onHabitClick = {
                            navController.navigate(Screen.AddEditHabit.route + "?habitId=${habit.id}")
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun HabitItem(habit: Habit, onHabitClick: (Habit) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onHabitClick(habit) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = habit.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = habit.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = habit.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
    HabitItem(
        habit = Habit(
            id = "1",
            title = "Читать книгу",
            description = "30 минут в день",
            icon = "📚",
            type = HabitType.PERSONAL,
            frequency = HabitFrequency.DAILY,
            createdBy = "user1",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        ),
        onHabitClick = {}
    )
}
