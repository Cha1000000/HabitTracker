package com.habittracker.presentation.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.TahoeIcons

data class PrivacySection(
    val titleResId: Int,
    val textResId: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    navController: NavController
) {
    val isDarkTheme = isSystemInDarkTheme()
    val scrollState = rememberScrollState()

    val sections = listOf(
        PrivacySection(R.string.privacy_policy_section_1_title, R.string.privacy_policy_section_1_text),
        PrivacySection(R.string.privacy_policy_section_2_title, R.string.privacy_policy_section_2_text),
        PrivacySection(R.string.privacy_policy_section_3_title, R.string.privacy_policy_section_3_text),
        PrivacySection(R.string.privacy_policy_section_4_title, R.string.privacy_policy_section_4_text),
        PrivacySection(R.string.privacy_policy_section_5_title, R.string.privacy_policy_section_5_text),
        PrivacySection(R.string.privacy_policy_section_6_title, R.string.privacy_policy_section_6_text),
        PrivacySection(R.string.privacy_policy_section_7_title, R.string.privacy_policy_section_7_text),
        PrivacySection(R.string.privacy_policy_section_8_title, R.string.privacy_policy_section_8_text),
        PrivacySection(R.string.privacy_policy_section_9_title, R.string.privacy_policy_section_9_text)
    )

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.privacy_policy_title)) },
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
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Last updated date
                Text(
                    text = stringResource(R.string.privacy_policy_last_updated),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Privacy policy sections
                sections.forEach { section ->
                    PrivacySectionItem(
                        title = stringResource(section.titleResId),
                        text = stringResource(section.textResId),
                        isDarkTheme = isDarkTheme
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PrivacySectionItem(
    title: String,
    text: String,
    isDarkTheme: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDarkTheme) {
                Color.White.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PrivacyPolicyScreenPreview() {
    MaterialTheme {
        PrivacyPolicyScreenContent(isDarkTheme = false)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PrivacyPolicyScreenDarkPreview() {
    MaterialTheme {
        PrivacyPolicyScreenContent(isDarkTheme = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicyScreenContent(isDarkTheme: Boolean) {
    val scrollState = rememberScrollState()

    val sections = listOf(
        Pair("1. Общие положения", "Настоящая Политика конфиденциальности описывает, как приложение Habit Tracker собирает, использует, хранит и защищает вашу персональную информацию."),
        Pair("2. Какую информацию мы собираем", "Мы собираем информацию об аккаунте (email, имя), информацию об устройстве, данные об использовании приложения, токен FCM для уведомлений."),
        Pair("3. Как мы используем вашу информацию", "Для предоставления функциональности, синхронизации данных, отправки уведомлений, улучшения приложения."),
        Pair("4. Хранение и защита данных", "Данные хранятся в Firebase Firestore с шифрованием и защищённой передачей."),
        Pair("5. Передача данных", "Мы не продаём данные. Передача только поставщикам услуг (Google Firebase) и по требованию закона."),
        Pair("6. Ваши права", "Право на доступ, исправление, удаление данных, экспорт, отзыв согласия."),
        Pair("7. Конфиденциальность детей", "Приложение не предназначено для лиц младше 16 лет."),
        Pair("8. Изменения в Политике", "Мы можем обновлять Политику. Проверяйте страницу периодически."),
        Pair("9. Свяжитесь с нами", "Email: racerkafa@yandex.ru")
    )

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text("Политика конфиденциальности") },
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
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Последнее обновление: 11 февраля 2026 г.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                sections.forEach { (title, text) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDarkTheme) {
                                Color.White.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
