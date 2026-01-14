package com.habittracker.presentation.ui.challenges

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habittracker.R
import com.habittracker.presentation.state.ChallengeTab
import com.habittracker.presentation.ui.components.GradientPullRefreshIndicator
import com.habittracker.presentation.ui.habits.components.ConfirmDialog
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.viewmodel.ChallengesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ChallengesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCreateChallenge: () -> Unit,
    onNavigateToChallengeDetails: (String) -> Unit,
    onNavigateToEditChallenge: (String) -> Unit,
    initialTab: String? = null,
    viewModel: ChallengesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(
        uiState.isLoading,
        uiState.errorMessage,
        uiState.pendingInvitations,
        uiState.activeChallenges,
        uiState.endingSoonChallenges,
        uiState.completedChallenges
    ) {
        if (!uiState.isLoading) {
            isRefreshing = false
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.onRefresh()
        }
    )
    
    // Устанавливаем начальную вкладку только один раз при первом запуске
    LaunchedEffect(Unit) {
        initialTab?.let { tab ->
            val challengeTab = when (tab.lowercase()) {
                "invitations" -> ChallengeTab.INVITATIONS
                "active" -> ChallengeTab.ACTIVE
                "ending_soon" -> ChallengeTab.ENDING_SOON
                "completed" -> ChallengeTab.COMPLETED
                else -> null
            }
            challengeTab?.let { viewModel.onTabSelected(it) }
        }
    }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.challenges_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToCreateChallenge) {
                            Icon(
                                imageVector = TahoeIcons.Plus,
                                contentDescription = stringResource(R.string.create_challenge)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
                        titleContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .pullRefresh(pullRefreshState)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Tab Row
                    TabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab.ordinal]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        ChallengeTab.entries.forEach { tab ->
                            val badgeCount = if (tab == ChallengeTab.INVITATIONS) uiState.pendingInvitations.size else 0
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = { viewModel.onTabSelected(tab) },
                                text = {
                                    Text(
                                        text = when (tab) {
                                            ChallengeTab.INVITATIONS -> stringResource(R.string.invitations)
                                            ChallengeTab.ACTIVE -> stringResource(R.string.active_challenges)
                                            ChallengeTab.ENDING_SOON -> stringResource(R.string.ending_soon)
                                            ChallengeTab.COMPLETED -> stringResource(R.string.completed_challenges)
                                        },
                                        fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab == ChallengeTab.INVITATIONS && badgeCount > 0) {
                                                Badge { Text("$badgeCount") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = when (tab) {
                                                ChallengeTab.INVITATIONS -> TahoeIcons.Envelope
                                                ChallengeTab.ACTIVE -> TahoeIcons.Group
                                                ChallengeTab.ENDING_SOON -> TahoeIcons.Clock
                                                ChallengeTab.COMPLETED -> TahoeIcons.Trophy
                                            },
                                            contentDescription = null
                                        )
                                    }
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
                                    ChallengeTab.INVITATIONS -> uiState.pendingInvitations
                                    ChallengeTab.ACTIVE -> uiState.activeChallenges
                                    ChallengeTab.ENDING_SOON -> uiState.endingSoonChallenges
                                    ChallengeTab.COMPLETED -> uiState.completedChallenges
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
                                        items(
                                            count = challenges.size,
                                            key = { index -> challenges[index].id }
                                        ) { index ->
                                            val challenge = challenges[index]
                                            if (uiState.selectedTab == ChallengeTab.INVITATIONS) {
                                                ChallengeInvitationCard(
                                                    challenge = challenge,
                                                    onAccept = { viewModel.acceptChallengeInvitation(challenge.id) },
                                                    onDecline = { viewModel.declineChallengeInvitation(challenge.id) },
                                                    onChallengeClick = { onNavigateToChallengeDetails(challenge.id) }
                                                )
                                            } else {
                                                ChallengeCard(
                                                    challenge = challenge,
                                                    currentUserId = uiState.currentUserId,
                                                    onChallengeClick = { onNavigateToChallengeDetails(challenge.id) },
                                                    onJoinClick = { viewModel.onJoinChallengeClick(challenge.id) },
                                                    onLeaveClick = { viewModel.onLeaveChallengeClick(challenge.id) },
                                                    onEditClick = { onNavigateToEditChallenge(challenge.id) }
                                                )
                                            }
                                        }
                                    }
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
 // Join Challenge Dialog (Tahoe)
    if (uiState.showJoinDialog) {
        ConfirmDialog(
            title = stringResource(R.string.join_challenge),
            message = stringResource(R.string.join_challenge_confirmation),
            confirmLabel = stringResource(R.string.join),
            cancelLabel = stringResource(R.string.cancel),
            onConfirm = { viewModel.onJoinChallengeConfirm() },
            onDismiss = { viewModel.onJoinChallengeCancel() }
        )
    }
    
    // Leave Challenge Dialog (Tahoe)
    if (uiState.showLeaveDialog) {
        ConfirmDialog(
            title = stringResource(R.string.leave_challenge_title),
            message = stringResource(R.string.leave_challenge_confirmation),
            confirmLabel = stringResource(R.string.leave),
            cancelLabel = stringResource(R.string.cancel),
            isDestructive = true,
            onConfirm = { viewModel.confirmLeaveChallenge() },
            onDismiss = { viewModel.dismissLeaveDialog() }
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
                ChallengeTab.INVITATIONS -> TahoeIcons.Envelope
                ChallengeTab.ACTIVE -> TahoeIcons.Group
                ChallengeTab.ENDING_SOON -> TahoeIcons.Clock
                ChallengeTab.COMPLETED -> TahoeIcons.Trophy
            },
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (tab) {
                ChallengeTab.INVITATIONS -> stringResource(R.string.no_challenge_invitations)
                ChallengeTab.ACTIVE -> stringResource(R.string.no_active_challenges)
                ChallengeTab.ENDING_SOON -> stringResource(R.string.no_ending_soon_challenges)
                ChallengeTab.COMPLETED -> stringResource(R.string.no_completed_challenges)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = when (tab) {
                ChallengeTab.INVITATIONS -> stringResource(R.string.no_challenge_invitations_description)
                ChallengeTab.ACTIVE -> stringResource(R.string.no_active_challenges_description)
                ChallengeTab.ENDING_SOON -> stringResource(R.string.no_ending_soon_challenges_description)
                ChallengeTab.COMPLETED -> stringResource(R.string.no_completed_challenges_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        if (tab == ChallengeTab.ACTIVE) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = onCreateChallenge) {
                Icon(
                    imageVector = TahoeIcons.Plus,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_challenge))
            }
        }
    }
}

@Composable
private fun ChallengeInvitationCard(
    challenge: com.habittracker.domain.model.Challenge,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onChallengeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onChallengeClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = challenge.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = challenge.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Награда: ${challenge.reward.ifBlank { "Не указана" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Принять")
                }
                
                TextButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отклонить")
                }
            }
        }
    }
}
