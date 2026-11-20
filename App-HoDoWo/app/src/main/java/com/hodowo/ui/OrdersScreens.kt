package com.hodowo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hodowo.data.DeliveryWindow
import com.hodowo.data.Order
import com.hodowo.data.OrderItem
import com.hodowo.data.OrderStatus
import com.hodowo.data.formatDeliveryWindow
import com.hodowo.data.formatPriceCents
import com.hodowo.ui.theme.Background
import com.hodowo.ui.theme.ErrorContainer
import com.hodowo.ui.theme.OnErrorContainer
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSecondaryContainer
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.OnTertiaryFixed
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.Secondary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.ui.theme.TertiaryFixed
import com.hodowo.viewmodel.OrderConfirmationState
import com.hodowo.viewmodel.OrderFilter
import com.hodowo.viewmodel.OrdersViewModel
import com.hodowo.viewmodel.Purchase
import com.hodowo.viewmodel.PurchaseHistoryState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// The deep indigo of the HoDoWo mockups.
private val OrdersPrimary = Color(0xFF26008C)
private val OrdersPrimaryContainer = Color(0xFF3B02C7)

// ====================== Order confirmation ======================

/**
 * Shown right after paying (#144): order code, date, status (followed until Stripe confirms),
 * one section per seller with its items and delivery, the payment summary and the next steps.
 */
@Composable
fun OrderConfirmationScreen(
    viewModel: OrdersViewModel,
    onViewOrders: () -> Unit,
    onBackToMarket: () -> Unit,
    onOpenItem: (OrderItem, Order) -> Unit
) {
    val state by viewModel.confirmation.collectAsState()
    val session = state.session

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        OrdersTopBar(title = "Order confirmation", onBack = onBackToMarket)
        if (session == null) {
            // Nothing to confirm (e.g. the app was restarted): the history has every purchase.
            OrdersMessage(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Your purchases are in your orders",
                text = "Open the purchase history to see them.",
                action = "View my orders",
                onAction = onViewOrders
            )
        } else {
            ConfirmationBody(
                state = state,
                viewModel = viewModel,
                onViewOrders = onViewOrders,
                onBackToMarket = onBackToMarket,
                onOpenItem = onOpenItem
            )
        }
    }
}

@Composable
private fun ColumnScope.ConfirmationBody(
    state: OrderConfirmationState,
    viewModel: OrdersViewModel,
    onViewOrders: () -> Unit,
    onBackToMarket: () -> Unit,
    onOpenItem: (OrderItem, Order) -> Unit
) {
    val session = state.session ?: return
    Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConfirmationHero(state)
            ProgressCard(state.status)

            if (state.isLoading && state.orders.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrdersPrimary)
                }
            }
            state.error?.let { error ->
                InlineError(text = error, onRetry = viewModel::retryConfirmation)
            }
            if (state.orders.isNotEmpty()) {
                SectionTitle(
                    icon = Icons.Default.Inventory2,
                    title = if (state.orders.size == 1) "Your items" else "Your items (${state.orders.size} sellers)"
                )
                state.orders.forEach { order ->
                    SellerOrderCard(order = order, onOpenItem = { onOpenItem(it, order) })
                }
            }

            PaymentSummaryCard(
                subtotalCents = state.orders.takeIf { it.isNotEmpty() }?.sumOf { it.subtotalCents } ?: session.subtotalCents,
                shippingCents = state.orders.takeIf { it.isNotEmpty() }?.sumOf { it.shippingCents } ?: session.shippingCents,
                totalCents = state.orders.takeIf { it.isNotEmpty() }?.sumOf { it.totalCents } ?: session.totalCents,
                currency = session.currency,
                paymentMethod = session.paymentMethod,
                simulated = state.simulated,
                paid = state.status == OrderStatus.PAID
            )

            Button(
                onClick = onViewOrders,
                colors = ButtonDefaults.buttonColors(containerColor = OrdersPrimaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("View my orders", fontWeight = FontWeight.SemiBold)
            }
            Surface(
                onClick = onBackToMarket,
                shape = RoundedCornerShape(12.dp),
                color = SurfaceContainer,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = OnSurface, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Back to the marketplace", color = OnSurface, fontWeight = FontWeight.SemiBold)
                }
            }
        }
}

@Composable
private fun ConfirmationHero(state: OrderConfirmationState) {
    val context = LocalContext.current
    val status = state.status
    val session = state.session ?: return
    val (icon, label, headline, text) = when (status) {
        OrderStatus.PAID -> HeroText(
            Icons.Default.CheckCircle,
            if (state.simulated) "PURCHASE RECORDED" else "PAYMENT CONFIRMED",
            "Order confirmed!",
            "Your order is in. You'll find it any time in your purchase history."
        )
        OrderStatus.FAILED, OrderStatus.CANCELLED -> HeroText(
            Icons.Default.ErrorOutline,
            "PAYMENT NOT COMPLETED",
            "Payment ${status.label.lowercase()}",
            "Nothing was charged and your cart still has the items."
        )
        else -> HeroText(
            Icons.Default.HourglassTop,
            "PAYMENT PROCESSING",
            "Almost done…",
            "Stripe is confirming the payment. This page updates by itself."
        )
    }
    val positive = status == OrderStatus.PAID
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerLowest, shadowElevation = 1.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(if (positive) OrdersPrimaryContainer else SurfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (positive) OnPrimary else OrdersPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = OrdersPrimary)
            Text(
                headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
            if (state.simulated) {
                Spacer(Modifier.height(10.dp))
                SimulatedBadge()
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaTile(label = "ORDER", modifier = Modifier.weight(1f)) {
                    val code = "#" + session.checkoutId.take(8).uppercase()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(code, fontWeight = FontWeight.SemiBold, color = OnSurface)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy order code",
                            tint = Secondary,
                            modifier = Modifier.size(16.dp).clickable { copyToClipboard(context, code) }
                        )
                    }
                }
                MetaTile(label = "DATE & TIME", modifier = Modifier.weight(1f)) {
                    val placedAt = state.orders.maxOfOrNull { it.createdAt }?.takeIf { it > 0 } ?: System.currentTimeMillis()
                    Text(formatDateTime(placedAt), fontWeight = FontWeight.SemiBold, color = OnSurface)
                }
            }
        }
    }
}

private data class HeroText(val icon: ImageVector, val label: String, val headline: String, val text: String)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Order", text))
    Toast.makeText(context, "Order code copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun MetaTile(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Secondary, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun SimulatedBadge() {
    Row(
        modifier = Modifier
            .background(TertiaryFixed, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.Science, contentDescription = null, tint = OnTertiaryFixed, modifier = Modifier.size(14.dp))
        Text("Simulated: no payment was taken", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnTertiaryFixed)
    }
}

/** Placed → paid → shipped / ready → delivered. The backend tracks the first two today. */
@Composable
private fun ProgressCard(status: OrderStatus) {
    if (status == OrderStatus.FAILED || status == OrderStatus.CANCELLED) return
    val steps = listOf("Placed", "Paid", "On its way", "Delivered")
    val current = if (status == OrderStatus.PAID) 1 else 0
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerLowest, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = OrdersPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Order progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "Step ${current + 1} of ${steps.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSecondaryContainer,
                    modifier = Modifier
                        .background(SecondaryContainer, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                steps.indices.forEach { index ->
                    val done = index <= current
                    Box(
                        modifier = Modifier
                            .size(if (index == current) 30.dp else 26.dp)
                            .background(if (done) OrdersPrimaryContainer else SurfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index < current || (index == current && status == OrderStatus.PAID)) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(16.dp))
                        } else if (index == current) {
                            Box(Modifier.size(10.dp).background(OnPrimary, CircleShape))
                        }
                    }
                    if (index < steps.lastIndex) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(if (index < current) OrdersPrimaryContainer else SurfaceContainerHigh)
                        )
                    }
                }
            }
            Row {
                steps.forEachIndexed { index, step ->
                    Text(
                        step,
                        fontSize = 11.sp,
                        fontWeight = if (index == current) FontWeight.Bold else FontWeight.Medium,
                        color = if (index <= current) OrdersPrimary else Secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** One seller's part of the purchase: how it arrives and what is in it. */
@Composable
private fun SellerOrderCard(order: Order, onOpenItem: (OrderItem) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerLowest, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).background(SurfaceContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (order.isPickup) Icons.Default.Storefront else Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = OrdersPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        methodLabel(order.shippingMethod).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Secondary,
                        letterSpacing = 0.5.sp
                    )
                    Text(order.sellerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                deliveryLabel(order)?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OrdersPrimary,
                        modifier = Modifier
                            .background(SurfaceContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            order.items.forEach { item -> OrderItemRow(item = item, currency = order.currency, onClick = { onOpenItem(item) }) }
            if (order.isPickup) {
                Text(
                    "Arrange the pickup with ${order.sellerName} in Messages" +
                        (order.pickupZip?.let { " (area $it)" } ?: "") + ".",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OrderItemRow(item: OrderItem, currency: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = OutlineVariant)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                typeLabel(item.productType),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Secondary
            )
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPriceCents(item.priceCents, currency), fontWeight = FontWeight.SemiBold, color = OnSurface)
            Text("Qty ${item.quantity}", style = MaterialTheme.typography.bodySmall, color = Secondary)
        }
    }
}

@Composable
private fun PaymentSummaryCard(
    subtotalCents: Int,
    shippingCents: Int,
    totalCents: Int,
    currency: String,
    paymentMethod: String?,
    simulated: Boolean,
    paid: Boolean
) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceContainerLowest, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Payment summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = Secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (simulated) "Simulated" else paymentMethodLabel(paymentMethod),
                    fontSize = 11.sp,
                    color = Secondary
                )
            }
            SummaryRow("Subtotal", formatPriceCents(subtotalCents, currency))
            SummaryRow("Shipping", if (shippingCents == 0) "Free" else formatPriceCents(shippingCents, currency), highlight = shippingCents == 0)
            Box(Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainerLow))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(if (paid && !simulated) "Total paid" else "Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("VAT included", fontSize = 11.sp, color = Secondary)
                }
                Text(
                    formatPriceCents(totalCents, currency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OrdersPrimary
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (highlight) OrdersPrimary else OnSurface)
    }
}

// ====================== Purchase history ======================

/** Every purchase (#144): search, status chips, pull to refresh, empty and error states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseHistoryScreen(
    viewModel: OrdersViewModel,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
    onOpenItem: (OrderItem, Order) -> Unit
) {
    val state by viewModel.history.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadHistory(refresh = state.loaded) }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        OrdersTopBar(title = "Purchase history", onBack = onBack)
        TextField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            placeholder = { Text("Search an order, item or seller…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceVariant) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = OnSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainer,
                unfocusedContainerColor = SurfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        StatusChips(state = state, onSelect = viewModel::onFilterSelected)

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.loadHistory(refresh = true) },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                state.isLoading && !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrdersPrimary)
                }
                state.error != null && state.purchases.isEmpty() -> OrdersMessage(
                    icon = Icons.Default.WifiOff,
                    title = "Couldn't load your orders",
                    text = state.error ?: "",
                    action = "Retry",
                    onAction = { viewModel.loadHistory() }
                )
                state.purchases.isEmpty() -> OrdersMessage(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "No purchases yet",
                    text = "What you buy on the marketplace will show up here.",
                    action = "Browse the marketplace",
                    onAction = onBrowse
                )
                state.visible.isEmpty() -> OrdersMessage(
                    icon = Icons.Default.Search,
                    title = "No orders found",
                    text = "Check the code or change the filters.",
                    action = "Show all orders",
                    onAction = {
                        viewModel.onQueryChanged("")
                        viewModel.onFilterSelected(OrderFilter.ALL)
                    }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    state.error?.let { error ->
                        item { InlineError(text = error, onRetry = { viewModel.loadHistory(refresh = true) }) }
                    }
                    items(state.visible, key = { it.checkoutId }) { purchase ->
                        PurchaseCard(purchase = purchase, onOpenItem = onOpenItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChips(state: PurchaseHistoryState, onSelect: (OrderFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrderFilter.entries.forEach { filter ->
            val selected = filter == state.filter
            val count = state.count(filter)
            Surface(
                onClick = { onSelect(filter) },
                shape = RoundedCornerShape(50),
                color = if (selected) OrdersPrimaryContainer else SurfaceContainer,
                shadowElevation = if (selected) 2.dp else 0.dp
            ) {
                Text(
                    text = if (state.loaded) "${filter.label} ($count)" else filter.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) OnPrimary else OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun PurchaseCard(purchase: Purchase, onOpenItem: (OrderItem, Order) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = SurfaceContainerLowest, shadowElevation = 1.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceContainerLow).padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(purchase.code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            dayLabel(purchase.createdAt),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrdersPrimary,
                            modifier = Modifier
                                .background(PrimaryFixed, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        "${purchase.itemCount} item${if (purchase.itemCount == 1) "" else "s"} • " +
                            "${purchase.orders.size} seller${if (purchase.orders.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPriceCents(purchase.totalCents, purchase.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    StatusChip(purchase.status)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (purchase.simulated) SimulatedBadge()
                purchase.orders.forEach { order ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (order.isPickup) Icons.Default.Storefront else Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            order.sellerName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            listOfNotNull(methodLabel(order.shippingMethod), deliveryLabel(order)).joinToString(" · "),
                            fontSize = 11.sp,
                            color = OnSurfaceVariant
                        )
                    }
                    order.items.forEach { item ->
                        OrderItemRow(item = item, currency = order.currency, onClick = { onOpenItem(item, order) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: OrderStatus) {
    val (background, content) = when (status) {
        OrderStatus.PAID -> SecondaryContainer to OnSecondaryContainer
        OrderStatus.PENDING -> TertiaryFixed to OnTertiaryFixed
        OrderStatus.FAILED, OrderStatus.CANCELLED -> ErrorContainer to OnErrorContainer
        OrderStatus.UNKNOWN -> SurfaceContainerHigh to OnSurfaceVariant
    }
    Text(
        status.label,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// ====================== shared ======================

@Composable
private fun OrdersTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = OnSurface)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = OnSurface)
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(icon, contentDescription = null, tint = Secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InlineError(text: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorContainer, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnErrorContainer, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Retry", color = OnErrorContainer, fontWeight = FontWeight.SemiBold) }
    }
}

/** Empty and error states; scrollable so pull to refresh works on them too. */
@Composable
private fun OrdersMessage(icon: ImageVector, title: String, text: String, action: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(56.dp).background(SurfaceContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = OnSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = OrdersPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text(action) }
        }
    }
}

private fun methodLabel(method: String?): String = when (method) {
    "pickup" -> "Local pickup"
    "express" -> "Express delivery"
    "standard" -> "Standard delivery"
    else -> "Delivery"
}

private fun deliveryLabel(order: Order): String? {
    val from = order.deliveryFrom ?: return null
    return formatDeliveryWindow(DeliveryWindow(from, order.deliveryTo ?: from)).takeIf { it.isNotBlank() }
}

private fun typeLabel(productType: String): String = when (productType) {
    "used" -> "SECOND-HAND"
    "part" -> "SPARE PART"
    else -> "NEW"
}

private fun paymentMethodLabel(method: String?): String = when (method) {
    "google_pay" -> "Google Pay"
    "klarna" -> "Klarna"
    "card" -> "Card"
    else -> "Stripe"
}

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).format(Date(millis))

/** "Today", "Yesterday" or the date. */
private fun dayLabel(millis: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        millis <= 0 -> ""
        sameDay(day, today) -> "Today"
        sameDay(day, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(millis))
    }
}
