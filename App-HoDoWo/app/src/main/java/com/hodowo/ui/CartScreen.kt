package com.hodowo.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import com.hodowo.data.Cart
import com.hodowo.data.CartLine
import com.hodowo.data.formatPriceCents
import com.hodowo.ui.theme.Error
import com.hodowo.ui.theme.ErrorContainer
import com.hodowo.ui.theme.OnErrorContainer
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryContainer
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.Secondary
import com.hodowo.ui.theme.Surface as SurfaceColor
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.CartViewModel

/**
 * "Shopping Cart & Checkout" screen (#142), following the Stitch design: lines grouped by
 * fulfilment (official partners / community pickup), quantity steppers, order summary and
 * the sticky "Place Order" button that starts the payment (#143).
 *
 * Only backend data is shown: totals, availability and price changes come from the cart
 * API. The design's delivery dates, fees, taxes and payment methods are not shown, because
 * the backend has no such data (the Stripe sheet will list the payment methods).
 * Text sizes come from the app typography (MaterialTheme.typography).
 */
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
    onBrowse: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(SurfaceColor)) {
        CartTopBar(onBack = onBack)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.cart.items.isEmpty() ->
                    CircularProgressIndicator(color = Primary, modifier = Modifier.align(Alignment.Center))

                state.error != null && state.cart.items.isEmpty() ->
                    CartMessage(
                        text = state.error ?: "",
                        buttonLabel = "Retry",
                        onClick = { viewModel.load() },
                        modifier = Modifier.align(Alignment.Center)
                    )

                state.cart.items.isEmpty() ->
                    CartMessage(
                        text = "Your cart is empty",
                        buttonLabel = "Browse the marketplace",
                        onClick = onBrowse,
                        modifier = Modifier.align(Alignment.Center)
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        CartMetaRow(
                            lineCount = state.cart.items.size,
                            enabled = state.updatingLineId == null,
                            onEmptyCart = { viewModel.clear() }
                        )
                    }
                    if (state.cart.hasUnavailableItems) {
                        item { UnavailableBanner() }
                    }
                    val (community, partner) = state.cart.items.partition { it.productType == "used" }
                    cartSection(
                        lines = partner,
                        title = "Official Partner Shipment",
                        icon = Icons.Default.VerifiedUser,
                        iconTint = Primary,
                        badge = "NEW",
                        state = state.cart,
                        updatingLineId = state.updatingLineId,
                        viewModel = viewModel
                    )
                    cartSection(
                        lines = community,
                        title = "Community Local Pickup",
                        icon = Icons.Default.NearMe,
                        iconTint = Secondary,
                        badge = "USED",
                        state = state.cart,
                        updatingLineId = state.updatingLineId,
                        viewModel = viewModel
                    )
                    item { OrderSummary(cart = state.cart) }
                }
            }
        }

        if (state.cart.items.isNotEmpty()) {
            CheckoutBar(cart = state.cart, onCheckout = onCheckout)
        }
    }
}

/** One fulfilment group: header plus its line cards. Nothing is shown for an empty group. */
private fun LazyListScope.cartSection(
    lines: List<CartLine>,
    title: String,
    icon: ImageVector,
    iconTint: Color,
    badge: String,
    state: Cart,
    updatingLineId: String?,
    viewModel: CartViewModel
) {
    if (lines.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title = title, icon = icon, iconTint = iconTint) }
    items(lines, key = { it.cartItemId }) { line ->
        CartLineCard(
            line = line,
            badge = badge,
            currency = state.currency,
            isUpdating = updatingLineId == line.cartItemId,
            onIncrease = { viewModel.increase(line) },
            onDecrease = { viewModel.decrease(line) },
            onRemove = { viewModel.remove(line) }
        )
    }
}

@Composable
private fun CartTopBar(onBack: () -> Unit) {
    Surface(color = SurfaceColor.copy(alpha = 0.95f), shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = OnSurface)
            }
            Text(
                text = "Shopping Cart & Checkout",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** "2 Items in Cart" on the left, "Empty Cart" on the right. */
@Composable
private fun CartMetaRow(lineCount: Int, enabled: Boolean, onEmptyCart: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(24.dp).background(PrimaryFixed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$lineCount",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (lineCount == 1) "Item in Cart" else "Items in Cart",
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onEmptyCart)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = Error, modifier = Modifier.size(16.dp))
            Text(text = "Empty Cart", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Error)
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, iconTint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = OnSurface)
    }
}

@Composable
private fun CartMessage(text: String, buttonLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(onClick = onClick) { Text(buttonLabel) }
    }
}

@Composable
private fun UnavailableBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = OnErrorContainer)
        Text(
            text = "Some items are no longer available. Remove them to continue to payment.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnErrorContainer
        )
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    badge: String,
    currency: String,
    isUpdating: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContainerLow)
            ) {
                AsyncImage(
                    model = line.imageUrl,
                    contentDescription = line.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (badge == "USED") Secondary else Primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(SurfaceContainerLowest.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = line.title.ifBlank { "Product" },
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRemove, enabled = !isUpdating, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove item",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                LineNotes(line = line, currency = currency)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinePrice(line = line, currency = currency, modifier = Modifier.weight(1f))
                    if (isUpdating) {
                        CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        QuantityStepper(
                            quantity = line.quantity,
                            canIncrease = line.available,
                            onDecrease = onDecrease,
                            onIncrease = onIncrease
                        )
                    }
                }
            }
        }
    }
}

/** Price of the line, or "Not available" when the backend can no longer sell it. */
@Composable
private fun LinePrice(line: CartLine, currency: String, modifier: Modifier = Modifier) {
    val linePrice = line.priceCents
    if (!line.available || linePrice == null) {
        Text(
            text = "Not available",
            style = MaterialTheme.typography.titleSmall,
            color = Error,
            modifier = modifier
        )
    } else {
        Text(
            text = formatPriceCents(linePrice, currency),
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface,
            modifier = modifier
        )
    }
}

/** Small grey line under the title: unit price, why it is unavailable, or a price change. */
@Composable
private fun LineNotes(line: CartLine, currency: String) {
    val unitPrice = line.unitPriceCents
    val addedPrice = line.addedPriceCents
    val note = when {
        !line.available -> line.unavailableReason?.replaceFirstChar { it.uppercase() } ?: "This item cannot be bought"
        line.priceChanged && addedPrice != null -> "Price changed: it was ${formatPriceCents(addedPrice, currency)}"
        line.quantity > 1 && unitPrice != null -> "${line.quantity} × ${formatPriceCents(unitPrice, currency)}"
        else -> null
    }
    if (note != null) {
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = if (!line.available) Error else OnSurfaceVariant
        )
    }
}

@Composable
private fun QuantityStepper(quantity: Int, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier.background(SurfaceContainerLow, RoundedCornerShape(8.dp)).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(icon = Icons.Default.Remove, description = "Decrease quantity", enabled = true, onClick = onDecrease)
        Text(
            text = "$quantity",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp)
        )
        StepperButton(icon = Icons.Default.Add, description = "Increase quantity", enabled = canIncrease, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) OnSurfaceVariant else OnSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun OrderSummary(cart: Cart) {
    val itemCount = cart.items.filter { it.available }.sumOf { it.quantity }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Order Summary", style = MaterialTheme.typography.titleSmall, color = OnSurface)
            SummaryRow(
                label = if (itemCount == 1) "Subtotal (1 item)" else "Subtotal ($itemCount items)",
                value = formatPriceCents(cart.totalCents, cart.currency)
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainerHigh))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Total Amount", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        text = "Shipping or pickup is agreed with each seller",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Text(
                    text = formatPriceCents(cart.totalCents, cart.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnSurface)
    }
}

/** Sticky bottom bar: secure-payment note and the "Place Order" button. */
@Composable
private fun CheckoutBar(cart: Cart, onCheckout: () -> Unit) {
    Surface(color = SurfaceColor.copy(alpha = 0.95f), shadowElevation = 8.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Secure payment with Stripe",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Test mode",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )
            }
            Button(
                onClick = onCheckout,
                enabled = cart.canCheckout,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimary)
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Place Order • ${formatPriceCents(cart.totalCents, cart.currency)}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}