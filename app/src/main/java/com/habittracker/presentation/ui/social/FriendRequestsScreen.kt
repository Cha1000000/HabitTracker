package com.habittracker.presentation.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.habittracker.domain.model.FriendRequestWithUsers
import com.habittracker.domain.model.getDisplayName
import com.habittracker.domain.model.getLastActiveText
import com.habittracker.domain.model.isOnline
import com.habittracker.domain.model.getCreatedTimeText
import com.habittracker.presentation.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    onNavigateBack: () -> Unit,
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
                        text = stringResource(R.string.friend_requests),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                uiState.incomingRequests.isEmpty() && uiState.outgoingRequests.isEmpty() -> {
                    EmptyRequestsState(
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Входящие запросы
                        if (uiState.incomingRequests.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = stringResource(R.string.incoming_requests),
                                    count = uiState.incomingRequests.size
                                )
                            }
                            
                            items(
                                items = uiState.incomingRequests,
                                key = { it.friendRequest.id }
                            ) { requestWithUsers ->
                                IncomingRequestCard(
                                    requestWithUsers = requestWithUsers,
                                    onAccept = { requestId ->
                                        viewModel.acceptFriendRequest(requestId)
                                    },
                                    onDecline = { requestId ->
                                        viewModel.declineFriendRequest(requestId)
                                    }
                                )
                            }
                        }
                        
                        // Исходящие запросы
                        if (uiState.outgoingRequests.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = stringResource(R.string.outgoing_requests),
                                    count = uiState.outgoingRequests.size
                                )
                            }
                            
                            items(
                                items = uiState.outgoingRequests,
                                key = { it.friendRequest.id }
                            ) { requestWithUsers ->
                                OutgoingRequestCard(
                                    requestWithUsers = requestWithUsers,
                                    onCancel = { requestId ->
                                        viewModel.cancelFriendRequest(requestId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptyRequestsState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.no_friend_requests),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Здесь будут отображаться входящие и исходящие запросы в друзья",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomingRequestCard(
    requestWithUsers: FriendRequestWithUsers,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fromUser = requestWithUsers.fromUser!!

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Аватар
                AsyncImage(
                    model = fromUser.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_person_24)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Информация о пользователе
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fromUser.getDisplayName(),
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
                                    color = if (fromUser.isOnline()) Color.Green else Color.Gray,
                                    radius = size.minDimension / 2
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = fromUser.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = stringResource(
                            R.string.request_sent_at,
                            requestWithUsers.friendRequest.getCreatedTimeText()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onAccept(requestWithUsers.friendRequest.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.accept_request))
                }
                
                OutlinedButton(
                    onClick = { onDecline(requestWithUsers.friendRequest.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.decline_request))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutgoingRequestCard(
    requestWithUsers: FriendRequestWithUsers,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val toUser = requestWithUsers.toUser!!

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
                model = toUser.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                fallback = androidx.compose.ui.res.painterResource(R.drawable.ic_person_24)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Информация о пользователе
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = toUser.getDisplayName(),
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
                                color = if (toUser.isOnline()) Color.Green else Color.Gray,
                                radius = size.minDimension / 2
                            )
                        }
                    }
                }
                
                Text(
                    text = toUser.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = stringResource(
                        R.string.request_sent_at,
                        requestWithUsers.friendRequest.getCreatedTimeText()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            // Кнопка отмены
            OutlinedButton(
                onClick = { onCancel(requestWithUsers.friendRequest.id) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.cancel_request))
            }
        }
    }
}
