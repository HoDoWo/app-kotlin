package com.hodowo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hodowo.ui.theme.Primary

/**
 * Single top banner shared by every main section of the app.
 *
 * It always shows the Howdowo brand mark, the app name and the section name
 * (e.g. "HOME", "AI CHAT", "PROFILE"). Screens add their own controls through
 * [actions], and detail screens can show a back arrow through [navigationIcon].
 *
 * The banner handles the status-bar inset itself, so screens must not add
 * `statusBarsPadding()` to the container that holds it.
 */
@Composable
fun AppTopBar(
    subtitle: String,
    modifier: Modifier = Modifier,
    title: String = "Howdowo",
    showLogo: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
                Spacer(modifier = Modifier.width(4.dp))
            }

            if (showLogo) {
                HoDoWoLogo(size = 38.dp)
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions
            )
        }
    }
}

/** Notification action shared by the top banners. */
@Composable
fun NotificationIconButton(onClick: () -> Unit = {}) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = "Notifications",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
