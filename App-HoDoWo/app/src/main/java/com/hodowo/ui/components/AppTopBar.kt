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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hodowo.ui.adaptive.LocalAppLayout
import com.hodowo.ui.theme.Primary

/**
 * Single top banner shared by every main section of the app.
 *
 * It always shows the app name and the section name (e.g. "HOME", "AI CHAT").
 * The HoDoWo logo is shown only when the vertical navigation rail is **not**
 * active — in landscape/tablet mode the rail already carries the brand mark,
 * so showing it twice would be redundant and odd-looking.
 *
 * Screens add their own controls through [actions]. When the rail is active,
 * the notification button and profile avatar live at the bottom of the rail
 * instead, so screens should suppress those actions in that mode.
 *
 * The banner handles the status-bar inset itself, so screens must not add
 * `statusBarsPadding()` to the container that holds it.
 */
@Composable
fun AppTopBar(
    subtitle: String,
    modifier: Modifier = Modifier,
    title: String = "Howdowo",
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // Hide the logo when the navigation rail is visible — it already carries
    // the brand mark in its header, and duplicating it looks cluttered.
    val showLogo = !LocalAppLayout.current.useNavigationRail

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

/** The bell of the app: how many new notifications there are and how to open them. */
data class NotificationBell(val badge: Int, val open: () -> Unit)

/** Provided by the root of the app, so every top banner shows the same bell. */
val LocalNotificationBell = compositionLocalOf<NotificationBell?> { null }

/**
 * Notification action shared by the top banners. Without [onClick] it opens the
 * notifications of [LocalNotificationBell] and shows how many are new.
 */
@Composable
fun NotificationIconButton(onClick: (() -> Unit)? = null) {
    val bell = LocalNotificationBell.current
    val badge = bell?.badge ?: 0
    // The badge is outside the button: inside it the round button would cut it off.
    BadgedBox(
        badge = {
            if (badge > 0) {
                Badge(containerColor = Primary) {
                    Text(
                        text = if (badge > MAX_BADGE) "$MAX_BADGE+" else "$badge",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        IconButton(onClick = onClick ?: bell?.open ?: {}, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = if (badge > 0) "Notifications, $badge new" else "Notifications",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private const val MAX_BADGE = 9
