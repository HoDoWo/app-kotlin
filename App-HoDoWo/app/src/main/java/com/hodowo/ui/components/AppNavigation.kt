package com.hodowo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
