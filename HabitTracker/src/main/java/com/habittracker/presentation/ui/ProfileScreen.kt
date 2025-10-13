package com.habittracker.presentation.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.habittracker.BuildConfig
import com.habittracker.R
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.habits.components.GlassmorphicButton
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.viewmodel.AuthEvent
import com.habittracker.presentation.viewmodel.AuthState
import com.habittracker.presentation.viewmodel.AuthViewModel
import com.habittracker.presentation.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val uiState by profileViewModel.state.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()

    // Handle navigation after successful sign out
    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated) {
            // Navigate to login screen and clear back stack
            navController.navigate("login_screen") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Оборачиваем весь экран в градиентный фон Tahoe
    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isSystemInDarkTheme()
    ) {
        Scaffold(
            containerColor = Color.Transparent, // Делаем фон Scaffold прозрачным
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.padding(vertical = 36.dp))
                // Avatar + name section
                Box(contentAlignment = Alignment.BottomEnd) {
                    val avatarSize = 112.dp
                    
                    // Launcher для выбора изображения
                    val imagePicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri -> 
                        android.util.Log.d("ProfileScreen", "Image picked: $uri")
                        profileViewModel.onAvatarPicked(uri) 
                    }
                    
                    // Launcher для запроса разрешения (только для Android 12 и ниже)
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        android.util.Log.d("ProfileScreen", "Permission result: $isGranted")
                        if (isGranted) {
                            imagePicker.launch("image/*")
                        } else {
                            // На Android 12 и ниже всё равно пробуем открыть picker
                            // Некоторые устройства позволяют выбрать изображение без разрешения
                            imagePicker.launch("image/*")
                        }
                    }

                    val displayData: Any? = uiState.pendingAvatarUri
                        ?: uiState.user?.avatarThumbnailUrl?.takeIf { it.isNotBlank() }
                        ?: uiState.user?.avatarUrl?.takeIf { it.isNotBlank() }
                    
                    if (displayData != null) {
                        // Поддержка data:image/* base64 без Coil
                        val isDataUrl = (displayData as? String)?.startsWith("data:image") == true
                        if (isDataUrl) {
                            val base64 = displayData.substringAfter(',')
                            val bytes =
                                runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
                            val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // fallback на AsyncImage (вдруг Coil сможет)
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(displayData)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(displayData)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(avatarSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        // Placeholder with initials
                        val initials = run {
                            val u = uiState.user
                            val name = u?.name?.ifBlank { u.email.substringBefore("@") } ?: ""
                            name.trim().split(" ")
                                .filter { it.isNotBlank() }
                                .take(2)
                                .map { it.first().uppercaseChar() }
                                .joinToString("")
                                .ifBlank { "?" }
                        }
                        Box(
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    if (uiState.isOwnProfile) {
                        // Glassmorphic круглая кнопка камеры
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(
                                    color = if (isDarkTheme) {
                                        Color.White.copy(alpha = 0.2f)
                                    } else {
                                        Color.White.copy(alpha = 0.8f)
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isDarkTheme) {
                                        Color.White.copy(alpha = 0.3f)
                                    } else {
                                        Color.Black.copy(alpha = 0.1f)
                                    },
                                    shape = CircleShape
                                )
                                .clickable {
                                    // Android 13+ использует Photo Picker без разрешений
                                    // Android 12 и ниже требует разрешение READ_EXTERNAL_STORAGE
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        imagePicker.launch("image/*")
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = if (isDarkTheme) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Username row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.profile_username_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (uiState.isEditingName) {
                            OutlinedTextField(
                                value = uiState.editableName,
                                onValueChange = profileViewModel::onNameChange,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val displayName =
                                uiState.user?.let { u -> u.name.ifBlank { u.email.substringBefore("@") } }
                                    ?: ""
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (uiState.isOwnProfile) {
                        IconButton(onClick = { profileViewModel.onToggleEditName() }) {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Friendship actions when viewing someone else's profile
                if (!uiState.isOwnProfile) {
                    when {
                        uiState.isFriend -> {
                            OutlinedButton(
                                onClick = { profileViewModel.removeFriend() },
                                enabled = !uiState.isActionInProgress,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(id = R.string.remove_friend))
                            }
                        }

                        uiState.isRequestSent -> {
                            Button(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(id = R.string.friend_request_sent))
                            }
                        }

                        else -> {
                            Button(
                                onClick = { profileViewModel.addFriend() },
                                enabled = !uiState.isActionInProgress,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(id = R.string.add_friend))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Level read-only
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(id = R.string.profile_level_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = (uiState.user?.level ?: 1).toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // Кнопки геймификации
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Кнопка статистики
                    GlassmorphicCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { navController.navigate(Screen.Stats.route) },
                        isDarkTheme = isDarkTheme
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = stringResource(R.string.cd_stats),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.profile_stats_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // Кнопка достижений
                    GlassmorphicCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { navController.navigate(Screen.Achievements.route) },
                        isDarkTheme = isDarkTheme
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = stringResource(R.string.cd_achievements),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.profile_achievements_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Save button (animated visibility on changes)
                AnimatedVisibility(
                    visible = uiState.hasChanges,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        GlassmorphicButton(
                            onClick = { profileViewModel.saveChanges() },
                            enabled = !uiState.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(text = stringResource(id = R.string.action_save))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sign out button
                GlassmorphicButton(
                    onClick = { authViewModel.onEvent(AuthEvent.SignOut) },
                    enabled = authState !is AuthState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            stringResource(R.string.action_sign_out),
                            fontSize = 16.sp,
                        )
                    }
                }

                // Display error if any
                if (authState is AuthState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = (authState as AuthState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Debug button for FCM testing
                if (BuildConfig.DEBUG) {
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassmorphicButton(onClick = { navController.navigate(Screen.FCMTest.route) }) {
                        Text("🧪 FCM Test")
                    }
                }
            }
        }
    }
}
