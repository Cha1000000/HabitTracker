package com.habittracker.presentation.ui.social

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.habittracker.R
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.User
import com.habittracker.presentation.viewmodel.FriendsViewModel

/**
 * 👥 Экран друзей - центральный hub социальных взаимодействий
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateToProfile: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    // Состояния диалога удаления друга
    var showRemoveDialog by remember { mutableStateOf(false) }
    var pendingRemoveFriendId by remember { mutableStateOf<String?>(null) }
    var alsoLeaveGroupHabits by remember { mutableStateOf(false) }
    val tabs = listOf(
        stringResource(id = R.string.friends),
        stringResource(id = R.string.friend_requests),
        stringResource(id = R.string.search_friends)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.friends_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        // 🎨 Modern Header с статистикой
        FriendsHeader(
            friendsCount = uiState.friends.size,
            pendingRequestsCount = uiState.incomingRequests.size
        )
        
        // 📱 Tab Navigation
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        when (index) {
                            0 -> Icon(Icons.Outlined.People, contentDescription = null)
                            1 -> BadgedBox(
                                badge = {
                                    if (uiState.incomingRequests.isNotEmpty()) {
                                        Badge { Text("${uiState.incomingRequests.size}") }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                            }
                            2 -> Icon(Icons.Outlined.Search, contentDescription = null)
                        }
                    }
                )
            }
        }
        
        // 📄 Tab Content с плавным переключением
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(durationMillis = 200)
        ) { tab ->
            when (tab) {
                0 -> FriendsTab(
                    friends = uiState.friends,
                    removingIds = uiState.removingIds,
                    onFriendClick = onNavigateToProfile,
                    onRemoveFriend = { id ->
                        pendingRemoveFriendId = id
                        alsoLeaveGroupHabits = false
                        showRemoveDialog = true
                    }
                )
                1 -> RequestsTab(
                    incomingRequests = uiState.incomingRequests,
                    outgoingRequests = uiState.outgoingRequests,
                    onAcceptRequest = { request -> viewModel.acceptFriendRequest(request.friendRequest.id) },
                    onDeclineRequest = { request -> viewModel.declineFriendRequest(request.friendRequest.id) },
                    onCancelRequest = { request -> viewModel.cancelFriendRequest(request.friendRequest.id) }
                )
                2 -> SearchTab(
                    searchQuery = uiState.searchQuery,
                    searchResults = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSendFriendRequest = { userId -> viewModel.sendFriendRequest(userId, "") }
                )
            }
        }
        }
    }
    // Диалог подтверждения удаления друга
    if (showRemoveDialog) {
        val pendingName = pendingRemoveFriendId?.let { id -> uiState.friends.firstOrNull { it.id == id }?.name } ?: ""
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(text = stringResource(id = R.string.confirm_remove_friend_title, pendingName)) },
            text = {
                Column {
                    Text(text = stringResource(id = R.string.confirm_remove_friend_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = alsoLeaveGroupHabits,
                            onCheckedChange = { alsoLeaveGroupHabits = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.also_leave_group_habits))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = pendingRemoveFriendId
                        if (id != null) {
                            viewModel.onRemoveFriendClicked(id, alsoLeaveGroupHabits)
                        }
                        showRemoveDialog = false
                    }
                ) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

/**
 * 👥 Вкладка "Друзья" - список всех друзей пользователя
 */
@Composable
private fun FriendsTab(
    friends: List<User>,
    removingIds: Set<String>,
    onFriendClick: (String) -> Unit,
    onRemoveFriend: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        if (friends.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.People,
                    title = stringResource(id = R.string.no_friends),
                    subtitle = stringResource(id = R.string.friends_empty_hint_search)
                )
            }
        } else {
            items(friends, key = { it.id }) { friend ->
                FriendCard(
                    friend = friend,
                    isRemoving = removingIds.contains(friend.id),
                    onClick = { onFriendClick(friend.id) },
                    onRemove = { onRemoveFriend(friend.id) }
                )
            }
        }
    }
}

/**
 * 📬 Вкладка "Запросы" - входящие и исходящие запросы дружбы
 */
@Composable
private fun RequestsTab(
    incomingRequests: List<FriendRequestWithUsers>,
    outgoingRequests: List<FriendRequestWithUsers>, 
    onAcceptRequest: (FriendRequestWithUsers) -> Unit,
    onDeclineRequest: (FriendRequestWithUsers) -> Unit,
    onCancelRequest: (FriendRequestWithUsers) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Входящие запросы
        if (incomingRequests.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(id = R.string.incoming_requests_count, incomingRequests.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(incomingRequests, key = { it.friendRequest.id }) { request ->
                IncomingRequestCard(
                    request = request,
                    onAccept = { onAcceptRequest(request) },
                    onDecline = { onDeclineRequest(request) }
                )
            }
        }
        
        // Исходящие запросы
        if (outgoingRequests.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.outgoing_requests_count, outgoingRequests.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(outgoingRequests, key = { it.friendRequest.id }) { request ->
                OutgoingRequestCard(
                    request = request,
                    onCancel = { onCancelRequest(request) }
                )
            }
        }
        
        // Пустое состояние
        if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Outlined.PersonAdd,
                    title = stringResource(id = R.string.no_requests_title),
                    subtitle = stringResource(id = R.string.no_requests_subtitle)
                )
            }
        }
    }
}

/**
 * 🔍 Вкладка "Поиск" - поиск новых друзей
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable 
private fun SearchTab(
    searchQuery: String,
    searchResults: List<User>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSendFriendRequest: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Поисковая строка
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(id = R.string.search_placeholder_friends)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(id = R.string.clear))
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Результаты поиска
        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (searchResults.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults, key = { it.id }) { user ->
                    SearchResultCard(
                        user = user,
                        onSendRequest = { onSendFriendRequest(user.id) }
                    )
                }
            }
        } else if (searchQuery.isNotEmpty()) {
            EmptyStateCard(
                icon = Icons.Outlined.SearchOff,
                title = stringResource(id = R.string.search_nothing_found_title),
                subtitle = stringResource(id = R.string.search_try_different_query)
            )
        } else {
            EmptyStateCard(
                icon = Icons.Outlined.Search,
                title = stringResource(id = R.string.search_friends),
                subtitle = stringResource(id = R.string.search_enter_name_prompt)
            )
        }
    }
}

/**
 * 🎨 Красивый header с статистикой и градиентом
 */
@Composable
private fun FriendsHeader(
    friendsCount: Int,
    pendingRequestsCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.friends_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Статистика друзей
                StatCard(
                    icon = Icons.Default.People,
                    count = friendsCount,
                    label = stringResource(id = R.string.friends_count_label)
                )
                
                // Статистика запросов
                StatCard(
                    icon = Icons.Default.PersonAdd,
                    count = pendingRequestsCount,
                    label = stringResource(id = R.string.requests_label)
                )
            }
        }
    }
}

/**
 * 📊 Компонент статистики для header
 */
@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

/**
 * 🎴 Карточка друга с аватаром и действиями
 */
@Composable
private fun FriendCard(
    friend: User,
    isRemoving: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (!isRemoving) base.clickable { onClick() } else base },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(friend.avatarThumbnailUrl ?: friend.avatarUrl ?: "")
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = friend.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Информация
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = stringResource(id = R.string.level_points_format, friend.level, friend.points),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                // Статус онлайн
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = Color.Green,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.online),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green
                    )
                }
            }
            
            // Меню действий / Лоадер
            Box {
                if (isRemoving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = { showMenu = true }, enabled = !isRemoving) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(id = R.string.cd_more)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.remove_friend)) },
                            onClick = {
                                onRemove()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PersonRemove,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 📭 Карточка пустого состояния
 */
@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 📬 Карточка входящего запроса дружбы
 */
@Composable
private fun IncomingRequestCard(
    request: FriendRequestWithUsers,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Аватар отправителя
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(request.fromUser.avatarThumbnailUrl ?: request.fromUser.avatarUrl ?: "")
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .build(),
                    contentDescription = request.fromUser.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.fromUser.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.wants_to_add_you),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Принять
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.accept_request))
                }
                
                // Отклонить
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.decline_request))
                }
            }
        }
    }
}

/**
 * 📤 Карточка исходящего запроса дружбы
 */
@Composable
private fun OutgoingRequestCard(
    request: FriendRequestWithUsers,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар получателя
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(request.toUser.avatarThumbnailUrl ?: request.toUser.avatarUrl ?: "")
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = request.toUser.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.toUser.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.request_sent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Кнопка отмены
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    }
}

/**
 * 🔍 Карточка результата поиска
 */
@Composable
private fun SearchResultCard(
    user: User,
    onSendRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(user.avatarThumbnailUrl ?: user.avatarUrl ?: "")
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .build(),
                contentDescription = user.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(id = R.string.level_points_format, user.level, user.points),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // Кнопка отправки запроса
            FilledTonalButton(
                onClick = onSendRequest,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(id = R.string.add_friend))
            }
        }
    }
}
