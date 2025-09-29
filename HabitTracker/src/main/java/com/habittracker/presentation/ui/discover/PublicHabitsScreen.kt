package com.habittracker.presentation.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.AutoMirrored.Filled
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.domain.model.Habit
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicSearchBar
import com.habittracker.presentation.ui.habits.components.TahoeFilterChip
import com.habittracker.presentation.ui.habits.components.TahoeTopBar
import com.habittracker.presentation.viewmodel.PublicHabitsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale.getDefault

/**
 * Категории публичных привычек
 */
enum class HabitCategory(
    val icon: ImageVector,
    val emoji: String,
    val stringResId: Int
) {
    ALL(Icons.Default.Apps, "✨", R.string.public_habits_category_all),
    HEALTH(Icons.Default.FitnessCenter, "💪", R.string.public_habits_category_health),
    SPORT(Icons.Default.SportsTennis, "⚽", R.string.public_habits_category_sport),
    EDUCATION(Icons.Default.School, "📚", R.string.public_habits_category_education),
    PRODUCTIVITY(Filled.TrendingUp, "📈", R.string.public_habits_category_productivity),
    MINDFULNESS(Icons.Default.SelfImprovement, "🧘", R.string.public_habits_category_mindfulness),
    CREATIVITY(Icons.Default.Palette, "🎨", R.string.public_habits_category_creativity),
    SOCIAL(Icons.Default.Groups, "👥", R.string.public_habits_category_social),
    OTHER(Icons.Default.Category, "🔮", R.string.public_habits_category_other)
}

@Composable
fun HabitCategory.getDisplayName(): String = stringResource(stringResId)

/**
 * Экран каталога публичных привычек
 * Позволяет просматривать, искать и присоединяться к публичным привычкам
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicHabitsScreen(
    navController: NavController,
    viewModel: PublicHabitsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var showJoinCodeDialog by remember { mutableStateOf(false) }
    var selectedHabit by remember { mutableStateOf<Habit?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (MaterialTheme.colorScheme.surface == Color.White) {
                        listOf(Color(0xFFF0F4FF), Color.White)
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    }
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar с поиском
            TahoeTopBar(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = stringResource(R.string.public_habits_title),
                subtitle = stringResource(R.string.public_habits_subtitle),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Кнопка ввода кода
                    FilledTonalButton(
                        onClick = { showJoinCodeDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.join_by_code_label), fontSize = 14.sp)
                    }
                },
            )
            
            // Поисковая строка
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassmorphicSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    placeholder = stringResource(R.string.public_habits_search_hint),
                    onClear = { viewModel.onSearchQueryChanged("") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Фильтры по категориям
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(HabitCategory.entries) { category ->
                    TahoeFilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(category.emoji, fontSize = 16.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(category.getDisplayName(), fontSize = 14.sp)
                            }
                        }
                    )
                }
            }
            
            // Сортировка
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.public_habits_found)}: ${uiState.filteredHabits.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                TextButton(
                    onClick = { viewModel.toggleSortOrder() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.sortByPopularity) Icons.Default.TrendingUp else Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (uiState.sortByPopularity) R.string.public_habits_sort_popularity 
                            else R.string.public_habits_sort_newest
                        ),
                        fontSize = 14.sp
                    )
                }
            }
            
            // Список привычек
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.filteredHabits.isEmpty() -> {
                    EmptyStateView(
                        searchQuery = uiState.searchQuery,
                        onCreateHabit = {
                            navController.navigate("add_edit_habit_screen")
                        }
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.filteredHabits) { habit ->
                            PublicHabitCard(
                                habit = habit,
                                onClick = {
                                    selectedHabit = habit
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Диалог ввода кода
        if (showJoinCodeDialog) {
            JoinByCodeDialog(
                onDismiss = { showJoinCodeDialog = false },
                onJoin = { code ->
                    showJoinCodeDialog = false
                    viewModel.joinByCode(code)
                }
            )
        }
        
        // Bottom Sheet с превью привычки
        selectedHabit?.let { habit ->
            HabitPreviewBottomSheet(
                habit = habit,
                onDismiss = { selectedHabit = null },
                onJoin = {
                    viewModel.joinHabit(habit)
                    selectedHabit = null
                }
            )
        }
    }
}

/**
 * Карточка публичной привычки
 */
@Composable
fun PublicHabitCard(
    habit: Habit,
    onClick: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        isDarkTheme = MaterialTheme.colorScheme.surface != Color.White,
        cornerRadius = 16.dp,
        glassAlpha = 0.1f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Иконка и название
            Column {
                Text(
                    text = habit.icon,
                    fontSize = 32.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Информация о привычке
            Column {
                // Частота
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = getFrequencyText(habit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Участники
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${habit.participants.size}/${habit.maxParticipants}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Диалог ввода кода приглашения
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.join_by_code_title))
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.join_by_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code.uppercase(),
                    onValueChange = { newCode ->
                        if (newCode.length <= 6) {
                            code = newCode.uppercase()
                            isError = false
                        }
                    },
                    label = { Text("Код") },
                    isError = isError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (isError) {
                        { Text("Код должен содержать 6 символов") }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (code.length == 6) {
                        onJoin(code)
                    } else {
                        isError = true
                    }
                },
                enabled = code.isNotEmpty()
            ) {
                Text(stringResource(R.string.join_by_code_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Bottom Sheet с предпросмотром привычки
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitPreviewBottomSheet(
    habit: Habit,
    onDismiss: () -> Unit,
    onJoin: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = habit.icon,
                    fontSize = 48.sp
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = habit.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (habit.description.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = habit.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Информационные карточки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoCard(
                    icon = Icons.Default.Group,
                    title = stringResource(R.string.public_habits_participants_title),
                    value = "${habit.participants.size}/${habit.maxParticipants}",
                    modifier = Modifier.weight(1f)
                )
                InfoCard(
                    icon = Icons.Default.Schedule,
                    title = stringResource(R.string.public_habits_frequency_title),
                    value = getFrequencyText(habit),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoCard(
                    icon = Icons.Default.CalendarToday,
                    title = stringResource(R.string.public_habits_created_title),
                    value = formatDate(habit.createdAt),
                    modifier = Modifier.weight(1f)
                )
                InfoCard(
                    icon = Icons.Default.Flag,
                    title = stringResource(R.string.public_habits_goal_title),
                    value = "${habit.targetValue} ${habit.unit}",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Кнопка присоединения
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                enabled = habit.participants.size < habit.maxParticipants,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.GroupAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (habit.participants.size >= habit.maxParticipants) 
                            R.string.public_habits_group_full
                        else 
                            R.string.public_habits_join_habit
                    )
                )
            }
        }
    }
}

/**
 * Информационная карточка
 */
@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    GlassmorphicCard(
        modifier = modifier,
        isDarkTheme = MaterialTheme.colorScheme.surface != Color.White,
        cornerRadius = 12.dp,
        glassAlpha = 0.05f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Пустое состояние
 */
@Composable
fun EmptyStateView(
    searchQuery: String,
    onCreateHabit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔍",
            fontSize = 64.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (searchQuery.isNotEmpty()) 
                    R.string.public_habits_nothing_found
                else 
                    R.string.public_habits_no_public
            ),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (searchQuery.isNotEmpty())
                    R.string.public_habits_try_different
                else
                    R.string.public_habits_be_first
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (searchQuery.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCreateHabit,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.public_habits_create_habit))
            }
        }
    }
}

// Вспомогательные функции
@Composable
private fun getFrequencyText(habit: Habit): String {
    return when (habit.frequency) {
        com.habittracker.domain.model.HabitFrequency.DAILY -> stringResource(R.string.frequency_daily)
        com.habittracker.domain.model.HabitFrequency.WEEKLY -> stringResource(R.string.frequency_weekly)
        com.habittracker.domain.model.HabitFrequency.TWICE_A_WEEK -> stringResource(R.string.frequency_twice_weekly)
        com.habittracker.domain.model.HabitFrequency.WEEKDAYS -> stringResource(R.string.frequency_weekdays)
        com.habittracker.domain.model.HabitFrequency.WEEKENDS -> stringResource(R.string.frequency_weekends)
        com.habittracker.domain.model.HabitFrequency.CUSTOM -> stringResource(R.string.frequency_custom)
    }
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("dd.MM.yy", getDefault())
    return format.format(date)
}
