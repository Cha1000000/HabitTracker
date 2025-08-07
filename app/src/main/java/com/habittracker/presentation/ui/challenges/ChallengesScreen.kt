package com.habittracker.presentation.ui.challenges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.domain.model.Challenge
import com.habittracker.presentation.viewmodel.ChallengeTab
import com.habittracker.presentation.viewmodel.ChallengesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesScreen(
    onNavigateToCreateChallenge: () -> Unit,
    onNavigateToChallengeDetails: (String) -> Unit,
    viewModel: ChallengesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.challenges_title),
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onNavigateToCreateChallenge) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.create_challenge)
                    )
                }
            }
        )

        // Tab Row
        TabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            ChallengeTab.values().forEach { tab ->
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = {
                        Text(
                            text = when (tab) {
                                ChallengeTab.ACTIVE -> stringResource(R.string.active_challenges)
                                ChallengeTab.ENDING_SOON -> stringResource(R.string.ending_soon)
                                ChallengeTab.COMPLETED -> stringResource(R.string.completed_challenges)
                            }
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                ChallengeTab.ACTIVE -> Icons.Default.Group
                                ChallengeTab.ENDING_SOON -> Icons.Default.Schedule
                                ChallengeTab.COMPLETED -> Icons.Default.EmojiEvents
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.errorMessage != null -> {
                    val errorMsg = uiState.errorMessage
                    if (errorMsg != null) {
                        ErrorMessage(
                            message = errorMsg,
                            onRetry = { viewModel.onRefresh() },
                            onDismiss = { viewModel.clearError() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                else -> {
                    val challenges = when (uiState.selectedTab) {
                        ChallengeTab.ACTIVE -> uiState.activeChallenges
                        ChallengeTab.ENDING_SOON -> uiState.endingSoonChallenges
                        ChallengeTab.COMPLETED -> emptyList() // TODO: Implement completed challenges
                    }

                    if (challenges.isEmpty()) {
                        EmptyState(
                            tab = uiState.selectedTab,
                            onCreateChallenge = onNavigateToCreateChallenge,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(challenges) { challenge ->
                                ChallengeCard(
                                    challenge = challenge,
                                    onChallengeClick = { onNavigateToChallengeDetails(challenge.id) },
                                    onJoinClick = { viewModel.onJoinChallengeClick(challenge.id) },
                                    onLeaveClick = { viewModel.onLeaveChallengeClick(challenge.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Join Challenge Dialog
    if (uiState.showJoinDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onJoinChallengeCancel() },
            title = {
                Text(stringResource(R.string.join_challenge))
            },
            text = {
                Text(stringResource(R.string.join_challenge_confirmation))
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onJoinChallengeConfirm() }
                ) {
                    Text(stringResource(R.string.join))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onJoinChallengeCancel() }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    tab: ChallengeTab,
    onCreateChallenge: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = when (tab) {
                ChallengeTab.ACTIVE -> Icons.Default.Group
                ChallengeTab.ENDING_SOON -> Icons.Default.Schedule
                ChallengeTab.COMPLETED -> Icons.Default.EmojiEvents
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (tab) {
                ChallengeTab.ACTIVE -> stringResource(R.string.no_active_challenges)
                ChallengeTab.ENDING_SOON -> stringResource(R.string.no_ending_soon_challenges)
                ChallengeTab.COMPLETED -> stringResource(R.string.no_completed_challenges)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = when (tab) {
                ChallengeTab.ACTIVE -> stringResource(R.string.no_active_challenges_description)
                ChallengeTab.ENDING_SOON -> stringResource(R.string.no_ending_soon_challenges_description)
                ChallengeTab.COMPLETED -> stringResource(R.string.no_completed_challenges_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (tab == ChallengeTab.ACTIVE) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = onCreateChallenge) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_first_challenge))
            }
        }
    }
}
