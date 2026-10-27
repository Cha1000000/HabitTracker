package com.habittracker.presentation.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.habittracker.R
import com.habittracker.presentation.navigation.Screen
import com.habittracker.presentation.ui.habits.components.GlassmorphicCard
import com.habittracker.presentation.ui.habits.components.GlassmorphicSectionHeader
import com.habittracker.presentation.ui.habits.components.TahoeGradientBackground
import com.habittracker.presentation.ui.theme.GlassBlack10
import com.habittracker.presentation.ui.theme.GlassBlack20
import com.habittracker.presentation.ui.theme.GlassWhite10
import com.habittracker.presentation.ui.theme.GlassWhite30
import com.habittracker.presentation.ui.theme.TahoeIcons
import com.habittracker.presentation.ui.theme.TahoeLightAccent
import com.habittracker.presentation.ui.theme.TahoeSkyBlue
import com.habittracker.presentation.viewmodel.DeleteAccountViewModel

/**
 * Удаление аккаунта: что пропадёт, что будет с группами, подтверждение словом и (для входа
 * по email) паролем. Кнопка закреплена внизу, чтобы действие было видно без прокрутки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    navController: NavController,
    viewModel: DeleteAccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()
    val activityContext = LocalContext.current

    var confirmText by rememberSaveable { mutableStateOf("") }
    // Пароль намеренно не в rememberSaveable — не кладём его в сохраняемое состояние
    var password by remember { mutableStateOf("") }
    val requiredWord = stringResource(R.string.delete_account_confirm_word)
    val wordMatches = confirmText.trim().equals(requiredWord, ignoreCase = true)
    val canConfirm = wordMatches && (!state.needsPassword || password.isNotEmpty()) && !state.isDeleting

    LaunchedEffect(state.isDeleted, state.isSignedOut) {
        if (state.isDeleted || state.isSignedOut) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Пока идёт удаление, назад не уходим: экран должен дождаться результата
    BackHandler(enabled = state.isDeleting) { }

    TahoeGradientBackground(
        modifier = Modifier.fillMaxSize(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.delete_account_title)) },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            enabled = !state.isDeleting
                        ) {
                            Icon(
                                imageVector = TahoeIcons.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                DeleteButtonBar(
                    enabled = canConfirm,
                    isDeleting = state.isDeleting,
                    isDarkTheme = isDarkTheme,
                    errorRes = state.errorRes,
                    onClick = { viewModel.delete(password, activityContext) }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                WarningCard(email = state.email, isDarkTheme = isDarkTheme)

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.delete_account_section_deleted))
                GlassmorphicCard(modifier = Modifier.fillMaxWidth(), isDarkTheme = isDarkTheme) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DeletedItem(TahoeIcons.User, R.string.delete_account_item_profile_title, R.string.delete_account_item_profile_desc)
                        DeletedItem(TahoeIcons.CheckCircle, R.string.delete_account_item_habits_title, R.string.delete_account_item_habits_desc)
                        DeletedItem(TahoeIcons.Image, R.string.delete_account_item_photos_title, R.string.delete_account_item_photos_desc)
                        DeletedItem(TahoeIcons.Users, R.string.delete_account_item_social_title, R.string.delete_account_item_social_desc)
                        DeletedItem(TahoeIcons.Trophy, R.string.delete_account_item_achievements_title, R.string.delete_account_item_achievements_desc)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.delete_account_section_groups))
                GlassmorphicCard(modifier = Modifier.fillMaxWidth(), isDarkTheme = isDarkTheme) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(TahoeIcons.UserGroup, tint = if (isDarkTheme) TahoeSkyBlue else TahoeLightAccent)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.delete_account_groups_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryTextColor()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.delete_account_section_confirm))
                GlassmorphicCard(modifier = Modifier.fillMaxWidth(), isDarkTheme = isDarkTheme) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FieldLabel(stringResource(R.string.delete_account_confirm_prompt, requiredWord))
                        CompactGlassField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            placeholder = requiredWord,
                            label = stringResource(R.string.delete_account_confirm_prompt, requiredWord),
                            enabled = !state.isDeleting,
                            isDarkTheme = isDarkTheme,
                            // Без автозамены: клавиатура иначе «исправляет» слово и оно не совпадает
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false
                            ),
                            trailing = if (wordMatches) {
                                {
                                    Icon(
                                        imageVector = TahoeIcons.CheckCircle,
                                        contentDescription = stringResource(R.string.cd_delete_account_word_matches),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else null
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.needsPassword) {
                            FieldLabel(stringResource(R.string.delete_account_password_label))
                            CompactGlassField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = stringResource(R.string.auth_password_label),
                                label = stringResource(R.string.delete_account_password_label),
                                enabled = !state.isDeleting,
                                isDarkTheme = isDarkTheme,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.delete_account_google_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun WarningCard(email: String, isDarkTheme: Boolean) {
    val error = MaterialTheme.colorScheme.error
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, error.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        isDarkTheme = isDarkTheme
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TahoeIcons.Warning,
                    contentDescription = null,
                    tint = error,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.delete_account_warning_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = error
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                // Email может не подгрузиться — тогда без него, а не «Аккаунт  и все…»
                text = if (email.isNotBlank()) {
                    stringResource(R.string.delete_account_warning_text, email)
                } else {
                    stringResource(R.string.delete_account_warning_text_no_email)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Основной цвет текста, как на остальных экранах настроек. */
@Composable
private fun primaryTextColor(): Color =
    if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface

@Composable
private fun SectionHeader(text: String) {
    GlassmorphicSectionHeader(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun DeletedItem(icon: ImageVector, @StringRes titleRes: Int, @StringRes descRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor()
            )
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = primaryTextColor(),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** Невысокое поле в стеклянном стиле: стандартный OutlinedTextField здесь слишком громоздкий. */
@Composable
private fun CompactGlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    /** Подпись для TalkBack: видимая подпись стоит отдельным Text над полем. */
    label: String,
    enabled: Boolean,
    isDarkTheme: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val accent = if (isDarkTheme) TahoeSkyBlue else TahoeLightAccent
    val borderColor = when {
        focused -> accent
        isDarkTheme -> GlassWhite30
        else -> GlassBlack20
    }
    val textColor = if (isDarkTheme) Color.White else Color.Black
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        cursorBrush = SolidColor(accent),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(shape)
                    .background(if (isDarkTheme) GlassWhite10 else Color.White.copy(alpha = 0.7f))
                    .border(1.dp, borderColor, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor.copy(alpha = 0.35f)
                        )
                    }
                    innerTextField()
                }
                if (trailing != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailing()
                }
            }
        }
    )
}

@Composable
private fun DeleteButtonBar(
    enabled: Boolean,
    isDeleting: Boolean,
    isDarkTheme: Boolean,
    @StringRes errorRes: Int?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Ошибка рядом с кнопкой: так её видно при любой прокрутке формы
        errorRes?.let { res ->
            // Своя подложка: под панелью яркий градиент, красный текст прямо на нём не читается
            Row(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    // TalkBack зачитает ошибку сам: фокус в это время на кнопке
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = TahoeIcons.ExclamationCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                // Неактивная кнопка — нейтральное «стекло», без красного: действие ещё не доступно
                disabledContainerColor = if (isDarkTheme) GlassWhite10 else GlassBlack10,
                disabledContentColor = primaryTextColor().copy(alpha = 0.45f)
            )
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.delete_account_in_progress))
            } else {
                Icon(imageVector = TahoeIcons.Trash, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.delete_account_button),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
