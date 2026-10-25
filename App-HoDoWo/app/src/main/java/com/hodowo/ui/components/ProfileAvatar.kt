package com.hodowo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Circular avatar shared by the top banners and the profile screen.
 *
 * Shows the uploaded photo when available, otherwise the first letter of
 * [name], and a generic person icon when there is neither.
 */
@Composable
fun ProfileAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
    photoUrl: String? = null,
    onClick: (() -> Unit)? = null
) {
    val sizedModifier = modifier.size(size.dp)

    // Material3's clickable Surface keeps the ripple inside the circle.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = sizedModifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            AvatarContent(name = name, size = size, photoUrl = photoUrl)
        }
    } else {
        Surface(
            modifier = sizedModifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            AvatarContent(name = name, size = size, photoUrl = photoUrl)
        }
    }
}

/** Photo / initial / icon shown inside [ProfileAvatar]. */
@Composable
private fun AvatarContent(name: String, size: Int, photoUrl: String?) {
    when {
        !photoUrl.isNullOrBlank() -> AsyncImage(
            model = photoUrl,
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        name.isNotBlank() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = if (size > 40) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        else -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size * 0.6f).dp)
            )
        }
    }
}
