package com.habittracker.presentation.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.habittracker.R
import com.habittracker.domain.model.FriendshipWithUsers
import com.habittracker.domain.model.User
import com.habittracker.domain.model.getDisplayName
import com.habittracker.domain.model.getLastActiveText
import com.habittracker.domain.model.isOnline
import com.habittracker.domain.model.getAcceptedTimeText
import com.habittracker.presentation.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToRequests: () -> Unit,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.friends),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    // Кнопка запросов в друзья с badge
                    BadgedBox(
                        badge = {
                            if (uiState.incomingRequestsCount > 0) {
                                Badge {
                                    Text(uiState.incomingRequestsCount.toString())
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = onNavigateToRequests) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = stringResource(R.string.friend_requests)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSearch,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.search_friends)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                uiState.friends.isEmpty() -> {
                    EmptyFriendsState(
                        onSearchClick = onNavigateToSearch,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.friends_count, uiState.friends.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        items(
                            items = uiState.friends,
                            key = { it.friendship.userId1 + it.friendship.userId2 }
                        ) { friendshipWithUsers ->
                            FriendCard(
                                friendshipWithUsers = friendshipWithUsers,
                                currentUserId = uiState.currentUser?.id ?: "",
                                onRemoveFriend = { friendId ->
                                    viewModel.removeFriend(friendId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendsState(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.no_friends),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.search_friends),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.search_friends))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FriendCard(
    friendshipWithUsers: FriendshipWithUsers,
    currentUserId: String,
    onRemoveFriend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val friend = friendshipWithUsers.getOtherUser(currentUserId)
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }
    
    if (friend == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            AsyncImage(
                model = friend.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_person_24)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Информация о друге
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = friend.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Индикатор онлайн статуса
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            drawCircle(
                                color = if (friend.isOnline()) Color.Green else Color.Gray,
                                radius = size.minDimension / 2
                            )
                        }
                    }
                }
                
                Text(
                    text = if (friend.isOnline()) {
                        stringResource(R.string.online)
                    } else {
                        stringResource(R.string.last_seen, friend.getLastActiveText())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = stringResource(
                        R.string.friend_since, 
                        friendshipWithUsers.friendship.getAcceptedTimeText()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Кнопка удаления
            IconButton(
                onClick = { showRemoveDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_friend),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // Диалог подтверждения удаления
    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = {
                Text(stringResource(R.string.remove_friend))
            },
            text = {
                Text("Вы уверены, что хотите удалить ${friend.getDisplayName()} из друзей?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFriend(friend.id)
                        showRemoveDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.remove_friend),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
