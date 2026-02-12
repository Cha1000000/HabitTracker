package com.habittracker.presentation.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.habittracker.BuildConfig
import com.habittracker.R
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController
) {
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.about_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
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
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // App Icon with glass effect background
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = 0.8f),
                                        Color.White.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                },
                                radius = 60f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_round),
                        contentDescription = stringResource(R.string.cd_app_logo),
                        modifier = Modifier.size(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // App Name with gradient
                Text(
                    text = stringResource(R.string.about_app_name),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        brush = Brush.linearGradient(
                            colors = if (isDarkTheme) {
                                listOf(
                                    Color(0xFF47D2A0),
                                    Color(0xFF736AF0)
                                )
                            } else {
                                listOf(
                                    Color(0xFF0AC2A6),
                                    Color(0xFFA78BFA)
                                )
                            }
                        )
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = stringResource(R.string.about_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version
                Text(
                    text = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Divider
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Menu Items
                AboutMenuItem(
                    icon = TahoeIcons.LockClosed,
                    text = stringResource(R.string.about_privacy_policy),
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        navController.navigate(Screen.PrivacyPolicy.route)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                AboutMenuItem(
                    icon = TahoeIcons.Star,
                    text = stringResource(R.string.about_rate_app),
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        // Open RuStore app page
                        val rustoreUrl = "https://www.rustore.ru/catalog/app/com.habittracker"
                        val intent = Intent(Intent.ACTION_VIEW, rustoreUrl.toUri())
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Timber.e(e, "Cannot open RuStore")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                val shareSubject = stringResource(R.string.about_share_subject)
                val shareText = stringResource(R.string.about_share_text)
                AboutMenuItem(
                    icon = TahoeIcons.Share,
                    text = stringResource(R.string.about_share_app),
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        // Share app link
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                val feedbackEmail = stringResource(R.string.about_feedback_email)
                val feedbackSubject = stringResource(R.string.about_feedback_subject)
                AboutMenuItem(
                    icon = TahoeIcons.Envelope,
                    text = stringResource(R.string.about_feedback),
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        // Send email feedback
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:".toUri()
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(feedbackEmail))
                            putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
                        }
                        try {
                            context.startActivity(emailIntent)
                        } catch (e: ActivityNotFoundException) {
                            Timber.e(e, "No email app found")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                AboutMenuItem(
                    icon = TahoeIcons.Heart,
                    text = stringResource(R.string.about_donate),
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        // Placeholder for donate functionality
                        Timber.d("Donate clicked - coming soon")
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AboutMenuItem(
    icon: ImageVector,
    text: String,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        isDarkTheme = isDarkTheme
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(18.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AboutScreenPreview() {
    MaterialTheme {
        AboutScreenContent(isDarkTheme = false)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AboutScreenDarkPreview() {
    MaterialTheme {
        AboutScreenContent(isDarkTheme = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreenContent(isDarkTheme: Boolean) {
    val scrollState = rememberScrollState()

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text("О приложении") },
                    navigationIcon = {
                        IconButton(onClick = { }) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = "Назад"
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
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // App Icon with glass effect background
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        Color.White.copy(alpha = 0.15f),
                                        Color.White.copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                } else {
                                    listOf(
                                        Color.White.copy(alpha = 0.8f),
                                        Color.White.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                },
                                radius = 60f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // В превью используем placeholder вместо реальной иконки
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "HT",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // App Name with gradient
                Text(
                    text = "Habit Tracker",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        brush = Brush.linearGradient(
                            colors = if (isDarkTheme) {
                                listOf(
                                    Color(0xFF47D2A0),
                                    Color(0xFF736AF0)
                                )
                            } else {
                                listOf(
                                    Color(0xFF0AC2A6),
                                    Color(0xFFA78BFA)
                                )
                            }
                        )
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Создавайте привычки вместе",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version
                Text(
                    text = "Версия 1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Divider
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Menu Items
                AboutMenuItem(
                    icon = TahoeIcons.LockClosed,
                    text = "Политика конфиденциальности",
                    isDarkTheme = isDarkTheme,
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(12.dp))

                AboutMenuItem(
                    icon = TahoeIcons.Star,
                    text = "Оценить приложение",
                    isDarkTheme = isDarkTheme,
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(12.dp))

                AboutMenuItem(
                    icon = TahoeIcons.Share,
                    text = "Поделиться с друзьями",
                    isDarkTheme = isDarkTheme,
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(12.dp))

                AboutMenuItem(
                    icon = TahoeIcons.Envelope,
                    text = "Обратная связь",
                    isDarkTheme = isDarkTheme,
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(12.dp))

                AboutMenuItem(
                    icon = TahoeIcons.Heart,
                    text = "На кофе разработчику",
                    isDarkTheme = isDarkTheme,
                    onClick = { }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
