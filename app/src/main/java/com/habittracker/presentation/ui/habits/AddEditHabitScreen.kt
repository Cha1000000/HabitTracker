package com.habittracker.presentation.ui.habits

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.habittracker.presentation.viewmodel.AddEditHabitViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHabitScreen(
    navController: NavController,
    viewModel: AddEditHabitViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isHabitSaved) {
        if (uiState.isHabitSaved) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isLoading) "" else "Новая привычка") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.saveHabit() }) {
                Icon(Icons.Filled.Check, contentDescription = "Сохранить")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onTitleChange(it) },
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.onDescriptionChange(it) },
                label = { Text("Описание (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddEditHabitScreenPreview() {
    AddEditHabitScreen(navController = rememberNavController())
}
