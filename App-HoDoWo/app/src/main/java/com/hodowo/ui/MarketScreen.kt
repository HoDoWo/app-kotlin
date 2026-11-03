package com.hodowo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hodowo.data.DemoMarketplaceService
import com.hodowo.ui.components.AppTopBar

/**
 * Temporary stand-in for the Market tab until the real entry point (#138) and
 * search with filters (#139) are built: lets you open the three demo products
 * (new, second-hand, part) so [ProductDetailScreen] (#140) can be tried in the app.
 */
@Composable
fun MarketScreen(onOpenProduct: (productId: String) -> Unit) {
    val demoProducts = listOf(
        DemoMarketplaceService.NEW_PRODUCT.id to DemoMarketplaceService.NEW_PRODUCT.title,
        DemoMarketplaceService.SECOND_HAND_LISTING.id to DemoMarketplaceService.SECOND_HAND_LISTING.title,
        DemoMarketplaceService.PART_FITS.id to DemoMarketplaceService.PART_FITS.title,
        DemoMarketplaceService.PART_DOES_NOT_FIT.id to DemoMarketplaceService.PART_DOES_NOT_FIT.title,
        DemoMarketplaceService.PART_UNKNOWN.id to DemoMarketplaceService.PART_UNKNOWN.title
    )

    Scaffold(topBar = { AppTopBar(subtitle = "MARKET") }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            demoProducts.forEach { (id, title) ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().clickable { onOpenProduct(id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
