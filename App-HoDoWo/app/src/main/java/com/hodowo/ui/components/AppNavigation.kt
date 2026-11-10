package com.hodowo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainerHigh

/** Sections shown in the bottom navigation bar. */
enum class AppNavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    AI_CHAT("AI Chat", Icons.Default.Forum),
    MARKET("Market", Icons.Default.Storefront),
    AR_CORE("AR Core", Icons.Default.ViewInAr),
    PROFILE("Profile", Icons.Default.Person)
}

/**
 * Bottom navigation bar matching the HTML mock: rounded active pill, icon above
 * the label. The whole cell is clickable, including the icon.
 */
@Composable
fun BottomNavBar(
    currentTab: AppNavTab,
    onTabSelected: (AppNavTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, SurfaceContainerHigh.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppNavTab.values().forEach { tab ->
                val isSelected = currentTab == tab

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                color = if (isSelected) SecondaryContainer else Color.Transparent
                            )
                            .clickable { onTabSelected(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Vertical navigation rail used in landscape and on tablets, where the bottom
 * bar would waste the little vertical space the display has.
 *
 * It has a fixed width of 80.dp to stay pinned to the left edge of the screen,
 * leaving the rest of the display for the main screen content.
 *
 * In landscape mode the top bar no longer shows the brand logo or the
 * notification / profile actions — those would be redundant and visually
 * cluttered next to the rail. Instead this rail renders [bottomActions] below a
 * thin divider at the foot of the column, so the user still has quick access to
 * notifications and their profile photo.
 */
@Composable
fun AppNavigationRail(
    currentTab: AppNavTab,
    onTabSelected: (AppNavTab) -> Unit,
    modifier: Modifier = Modifier,
    /** Composable slot rendered at the very bottom of the rail (e.g. notification icon, avatar). */
    bottomActions: @Composable () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, SurfaceContainerHigh.copy(alpha = 0.6f))
    ) {
        NavigationRail(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight(),
            containerColor = Color.Transparent,
            header = {
                // Brand logo at the top — replaces the one that was in AppTopBar
                Box(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HoDoWoLogo(size = 32.dp)
                }
            }
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AppNavTab.values().forEach { tab ->
                val isSelected = currentTab == tab

                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = false,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        indicatorColor = SecondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom action strip: notification icon + profile avatar, separated
            // from the nav items by a subtle divider.
            HorizontalDivider(
                modifier = Modifier
                    .width(48.dp)
                    .padding(vertical = 4.dp),
                color = SurfaceContainerHigh.copy(alpha = 0.8f)
            )
            Column(
                modifier = Modifier
                    .width(80.dp)
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bottomActions()
            }
        }
    }
}

/** Extended "New chat" button shown on every section except the chat itself. */
@Composable
fun NewChatButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = Primary,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("New chat", fontWeight = FontWeight.SemiBold) },
        modifier = modifier
    )
}
