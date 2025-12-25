package com.habittracker.presentation.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun UserAvatar(
    data: Any?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentDescription: String? = null
) {
    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(backgroundColor)

    val displayData = data
        ?.let { if (it is String) it.takeIf(String::isNotBlank) else it }

    if (displayData is String && displayData.startsWith("data:image")) {
        val base64 = displayData.substringAfter(',')
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
        val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = baseModifier,
                contentScale = ContentScale.Crop
            )
        } else {
            UserAvatarPlaceholder(name = name, modifier = baseModifier)
        }
        return
    }

    if (displayData != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(displayData)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = baseModifier,
            contentScale = ContentScale.Crop
        )
    } else {
        UserAvatarPlaceholder(name = name, modifier = baseModifier)
    }
}

@Composable
fun UserAvatar(
    avatarThumbnailUrl: String?,
    avatarUrl: String?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentDescription: String? = null
) {
    UserAvatar(
        data = avatarThumbnailUrl?.takeIf { it.isNotBlank() } ?: avatarUrl?.takeIf { it.isNotBlank() },
        name = name,
        size = size,
        modifier = modifier,
        backgroundColor = backgroundColor,
        contentDescription = contentDescription
    )
}

@Composable
private fun UserAvatarPlaceholder(
    name: String?,
    modifier: Modifier = Modifier
) {
    val initials = extractInitials(name)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun extractInitials(name: String?): String {
    val raw = name?.trim().orEmpty()
    if (raw.isBlank()) return "?"

    val compact = raw.replace(Regex("\\s+"), " ")
    val letters = compact.filter { it.isLetter() }
    if (letters.isEmpty()) return "?"

    val uppercaseLetters = compact.filter { it.isLetter() && it.isUpperCase() }
    if (uppercaseLetters.length >= 2) {
        return uppercaseLetters.take(2).uppercase()
    }

    val firstLetterIndex = compact.indexOfFirst { it.isLetter() }
    val first = compact.getOrNull(firstLetterIndex) ?: return "?"

    if (first.isLowerCase()) {
        val secondUpper = compact.drop(firstLetterIndex + 1).firstOrNull { it.isLetter() && it.isUpperCase() }
        if (secondUpper == null) return first.uppercaseChar().toString()

        return ("$first" + secondUpper.toString())
            .uppercase()
            .take(2)
            .ifBlank { "?" }
    }

    val words = compact.split(' ').filter { it.isNotBlank() }
    if (words.size >= 2) {
        val a = words[0].firstOrNull { it.isLetter() }
        val b = words[1].firstOrNull { it.isLetter() }
        val result = ("${a ?: first}${b?.toString().orEmpty()}").uppercase().filter { it.isLetter() }.take(2)
        if (result.isNotBlank()) return result
    }

    return first.uppercaseChar().toString()
}
