package com.hodowo.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hodowo.data.CartGroup
import com.hodowo.data.CartLine
import com.hodowo.data.CartSummary
import com.hodowo.data.CheckoutSession
import com.hodowo.ui.adaptive.AdaptiveContent
import com.hodowo.data.DeliveryOption
import com.hodowo.data.PAYMENT_CARD
import com.hodowo.data.PAYMENT_GOOGLE_PAY
import com.hodowo.data.PAYMENT_KLARNA
import com.hodowo.data.SELLER_COMMUNITY
import com.hodowo.data.formatDeliveryWindow
import com.hodowo.data.formatPriceCents
import com.hodowo.ui.theme.Error
import com.hodowo.ui.theme.ErrorContainer
import com.hodowo.ui.theme.OnErrorContainer
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSecondaryContainer
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryContainer
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.Secondary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SecondaryFixed
import com.hodowo.ui.theme.Surface as SurfaceColor
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.viewmodel.CartUiState
import com.hodowo.viewmodel.CartViewModel
import com.hodowo.viewmodel.CheckoutViewModel
import com.hodowo.viewmodel.PaymentOutcome
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.PaymentSheetResultCallback

/**
 * "Shopping Cart & Checkout" screen (#142, #143), following the Stitch design: the cart split
 * by seller, a tick on every line to choose what to buy now, the delivery of each seller
 * (standard or express shipping, or local pickup), the order summary, the payment method and
 * the sticky "Place Order" button that opens the Stripe PaymentSheet.
 *
 * Every price, shipping cost and total comes from the backend quote, so the screen shows
 * exactly what the checkout charges. Shipping is simulated: no carrier is booked.
 * Text sizes come from the app typography (MaterialTheme.typography).
 */
@Composable
fun CartScreen(
    viewModel: CartViewModel,
    checkoutViewModel: CheckoutViewModel,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
    /** Opens the Product Detail page of a cart line (#111). */
    onOpenProduct: (CartLine) -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val checkout by checkoutViewModel.uiState.collectAsState()

    // Stripe PaymentSheet (#143): it must be created on every composition, before it is used.
    val paymentCallback = remember(checkoutViewModel) {
        PaymentSheetResultCallback { result -> checkoutViewModel.onPaymentResult(result.toOutcome()) }
    }
    val paymentSheet = remember(paymentCallback) { PaymentSheet.Builder(paymentCallback) }.build()

    LaunchedEffect(checkout.paymentToPresent) {
        val request = checkout.paymentToPresent ?: return@LaunchedEffect
        PaymentConfiguration.init(context, request.publishableKey)
        val configuration = PaymentSheet.Configuration.Builder(merchantDisplayName = "Howdowo").apply {
            if (request.paymentMethod == PAYMENT_GOOGLE_PAY) {
                googlePay(
                    PaymentSheet.GooglePayConfiguration(
                        environment = PaymentSheet.GooglePayConfiguration.Environment.Test,
                        countryCode = "ES",
                        currencyCode = "EUR"
                    )
                )
            }
        }.build()
        paymentSheet.presentWithPaymentIntent(request.clientSecret, configuration)
        checkoutViewModel.onPaymentPresented()
    }
    LaunchedEffect(checkout.message) {
        checkout.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            checkoutViewModel.consumeMessage()
        }
    }
    checkout.placedOrder?.let { order ->
        OrderPlacedDialog(order = order, onDismiss = { checkoutViewModel.dismissConfirmation() })
    }

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

                else -> AdaptiveContent(maxWidth = 1000.dp) {
                    CartContent(state = state, viewModel = viewModel, onOpenProduct = onOpenProduct)
                }
            }
        }

        if (state.cart.items.isNotEmpty()) {
            CheckoutBar(
                summary = state.cart.summary,
                currency = state.cart.currency,
                isQuoting = state.isQuoting,
                isPreparing = checkout.isPreparing,
                onCheckout = { checkoutViewModel.placeOrder(viewModel.selection()) }
            )
        }
    }
}

@Composable
private fun CartContent(state: CartUiState, viewModel: CartViewModel, onOpenProduct: (CartLine) -> Unit) {
    val cart = state.cart
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CartMetaRow(
                itemCount = cart.itemCount,
                allSelected = state.allSelected,
                enabled = state.updatingLineId == null,
                onSelectAll = { viewModel.selectAll(!state.allSelected) },
                onEmptyCart = { viewModel.clear() }
            )
        }
        cart.groups.forEach { group ->
            item(key = "group-${group.sellerId}") { GroupHeader(group) }
            items(state.linesOf(group), key = { it.cartItemId }) { line ->
                CartLineCard(
                    line = line,
                    currency = cart.currency,
                    isUpdating = state.updatingLineId == line.cartItemId,
                    onToggle = { viewModel.toggleSelected(line) },
                    onIncrease = { viewModel.increase(line) },
                    onDecrease = { viewModel.decrease(line) },
                    onRemove = { viewModel.remove(line) },
                    onOpen = { onOpenProduct(line) }
                )
            }
            item(key = "delivery-${group.sellerId}") {
                DeliveryCard(
                    group = group,
                    currency = cart.currency,
                    enabled = !state.isQuoting,
                    onChoose = { method -> viewModel.chooseDelivery(group.sellerId, method) }
                )
            }
        }
        // Lines the backend did not group (an older backend answer): still shown and buyable.
        val grouped = cart.groups.flatMap { it.cartItemIds }.toSet()
        val ungrouped = cart.items.filter { it.available && it.cartItemId !in grouped }
        items(ungrouped, key = { it.cartItemId }) { line ->
            CartLineCard(
                line = line,
                currency = cart.currency,
                isUpdating = state.updatingLineId == line.cartItemId,
                onToggle = { viewModel.toggleSelected(line) },
                onIncrease = { viewModel.increase(line) },
                onDecrease = { viewModel.decrease(line) },
                onRemove = { viewModel.remove(line) },
                onOpen = { onOpenProduct(line) }
            )
        }
        val unavailable = state.unavailableLines
        if (unavailable.isNotEmpty()) {
            item(key = "unavailable") { UnavailableBanner() }
            items(unavailable, key = { it.cartItemId }) { line ->
                CartLineCard(
                    line = line,
                    currency = cart.currency,
                    isUpdating = state.updatingLineId == line.cartItemId,
                    onToggle = {},
                    onIncrease = {},
                    onDecrease = { viewModel.decrease(line) },
                    onRemove = { viewModel.remove(line) },
                    onOpen = { onOpenProduct(line) }
                )
            }
        }
        item(key = "summary") { OrderSummary(summary = cart.summary, currency = cart.currency) }
        item(key = "payment") {
            PaymentMethods(
                methods = cart.paymentMethods,
                chosen = state.paymentMethod,
                onChoose = viewModel::choosePaymentMethod
            )
        }
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

/** "2 Items in Cart", "Select all" and "Empty Cart". */
@Composable
private fun CartMetaRow(
    itemCount: Int,
    allSelected: Boolean,
    enabled: Boolean,
    onSelectAll: () -> Unit,
    onEmptyCart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(24.dp).background(PrimaryFixed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$itemCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (itemCount == 1) "Item in Cart" else "Items in Cart",
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
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .selectable(selected = allSelected, role = Role.Checkbox, onClick = onSelectAll),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = allSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Primary),
                modifier = Modifier.padding(4.dp))
            Text(text = "Select all", style = MaterialTheme.typography.labelLarge, color = OnSurface)
        }
    }
}

/** "Official Partner Shipment" with the partner's pill, or the community seller's row. */
@Composable
private fun GroupHeader(group: CartGroup) {
    val community = group.sellerKind == SELLER_COMMUNITY
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (community) Icons.Default.NearMe else Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = if (community) Secondary else Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (community) "Community Seller" else "Official Partner Shipment",
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
                modifier = Modifier.weight(1f)
            )
            if (!community) Pill(text = group.sellerName, background = PrimaryFixed, color = Primary)
        }
        if (community) {
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp).background(SecondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials(group.sellerName),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnSecondaryContainer
                    )
                }
                Text(text = "Seller: ${group.sellerName}", style = MaterialTheme.typography.labelMedium, color = OnSurface)
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

@Composable
private fun CartMessage(text: String, buttonLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = OnSurfaceVariant, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onClick) { Text(buttonLabel) }
    }
}

@Composable
private fun UnavailableBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(ErrorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = OnErrorContainer)
        Text(
            text = "These items are no longer available and will not be bought. You can remove them.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnErrorContainer
        )
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    currency: String,
    isUpdating: Boolean,
    onToggle: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit = {}
) {
    val badge = when (line.productType) {
        "used" -> "USED"
        "part" -> "PART"
        else -> "NEW"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = line.selected,
                onCheckedChange = { onToggle() },
                enabled = line.available && !isUpdating,
                colors = CheckboxDefaults.colors(checkedColor = Primary)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (line.selected || !line.available) 1f else 0.6f)
                    .clickable(onClickLabel = "Open product", onClick = onOpen),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceContainerLow)
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove item", tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                    if (line.subtitle.isNotBlank()) {
                        Text(text = line.subtitle, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    LineNotes(line = line, currency = currency)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LinePrice(line = line, currency = currency, modifier = Modifier.weight(1f))
                        if (isUpdating) {
                            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            QuantityStepper(quantity = line.quantity, canIncrease = line.available,
                                onDecrease = onDecrease, onIncrease = onIncrease)
                        }
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
        Text(text = "Not available", style = MaterialTheme.typography.titleSmall, color = Error, modifier = modifier)
    } else {
        Text(text = formatPriceCents(linePrice, currency), style = MaterialTheme.typography.titleMedium, color = OnSurface,
            modifier = modifier)
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
        Text(text = note, style = MaterialTheme.typography.labelSmall, color = if (!line.available) Error else OnSurfaceVariant)
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
        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick),
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

// ---------- delivery ----------

/** The delivery options of one seller, as radio rows with price and dates. */
@Composable
private fun DeliveryCard(group: CartGroup, currency: String, enabled: Boolean, onChoose: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SurfaceContainerLow, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "Delivery", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = OnSurface)
        if (group.selectedCount == 0) {
            Text(
                text = "Tick an item to buy from ${group.sellerName}",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            return@Column
        }
        group.options.forEach { option ->
            DeliveryRow(
                option = option,
                selected = option.method == group.method,
                pickupArea = group.pickupArea,
                currency = currency,
                enabled = enabled,
                onClick = { onChoose(option.method) }
            )
        }
    }
}

@Composable
private fun DeliveryRow(
    option: DeliveryOption,
    selected: Boolean,
    pickupArea: String?,
    currency: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val icon = when (option.method) {
        "express" -> Icons.Default.Bolt
        "pickup" -> Icons.Default.PinDrop
        else -> Icons.Default.LocalShipping
    }
    val detail = option.delivery?.let { "Estimated delivery: ${formatDeliveryWindow(it)}" }
        ?: if (pickupArea != null && selected) "Pickup area $pickupArea · arrange it with the seller after paying"
        else "Arrange the pickup with the seller after paying"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) SurfaceContainerLowest else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = Primary))
        Spacer(modifier = Modifier.width(8.dp))
        Icon(imageVector = icon, contentDescription = null, tint = if (selected) Primary else OnSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = option.label, style = MaterialTheme.typography.labelLarge, color = OnSurface)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        Text(
            text = if (option.priceCents == 0) "FREE" else formatPriceCents(option.priceCents, currency),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (option.priceCents == 0) Primary else OnSurface
        )
    }
}

// ---------- summary and payment ----------

@Composable
private fun OrderSummary(summary: CartSummary, currency: String) {
    val count = summary.selectedItemCount
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Order Summary", style = MaterialTheme.typography.titleSmall, color = OnSurface)
            SummaryRow(
                label = if (count == 1) "Subtotal (1 item)" else "Subtotal ($count items)",
                value = formatPriceCents(summary.subtotalCents, currency)
            )
            SummaryRow(
                label = "Shipping",
                value = if (summary.shippingCents == 0) "FREE" else formatPriceCents(summary.shippingCents, currency),
                highlight = summary.shippingCents == 0
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainerHigh))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Total Amount", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    Text(
                        text = "VAT included (${formatPriceCents(summary.vatIncludedCents, currency)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Text(
                    text = formatPriceCents(summary.totalCents, currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (highlight) Primary else OnSurface)
    }
}

private data class PaymentChoice(val title: String, val detail: String, val icon: ImageVector)

private val PAYMENT_CHOICES = mapOf(
    PAYMENT_CARD to PaymentChoice("Credit / Debit Card", "Visa, Mastercard and other cards", Icons.Default.CreditCard),
    PAYMENT_GOOGLE_PAY to PaymentChoice("Google Pay", "Pay with a card saved in your Google account", Icons.Default.Contactless),
    PAYMENT_KLARNA to PaymentChoice("Klarna", "Pay later or in instalments, if eligible", Icons.Default.Payments)
)

@Composable
private fun PaymentMethods(methods: List<String>, chosen: String, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(text = "Select Payment Method", style = MaterialTheme.typography.titleSmall, color = OnSurface)
        methods.mapNotNull { method -> PAYMENT_CHOICES[method]?.let { method to it } }.forEach { (method, choice) ->
            val selected = method == chosen
            Card(
                modifier = Modifier.fillMaxWidth()
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(method) }),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
                border = if (selected) BorderStroke(1.5.dp, Primary) else null,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(SurfaceContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = choice.icon, contentDescription = null, tint = if (selected) Primary else OnSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = choice.title, style = MaterialTheme.typography.titleSmall, color = OnSurface)
                        Text(text = choice.detail, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Primary))
                }
            }
        }
        Pill(text = "Test mode: no real money is taken", background = SecondaryFixed, color = OnSurface)
    }
}

/** Sticky bottom bar: secure-payment note and the "Place Order" button. */
@Composable
private fun CheckoutBar(
    summary: CartSummary,
    currency: String,
    isQuoting: Boolean,
    isPreparing: Boolean,
    onCheckout: () -> Unit
) {
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
                Text(text = "Test mode", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Primary)
            }
            Button(
                onClick = onCheckout,
                enabled = summary.canCheckout && !isQuoting && !isPreparing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimary)
            ) {
                when {
                    isPreparing -> {
                        CircularProgressIndicator(color = OnPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Securing payment…", style = MaterialTheme.typography.labelLarge)
                    }
                    summary.selectedItemCount == 0 -> Text(text = "Select items to buy", style = MaterialTheme.typography.labelLarge)
                    else -> {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Place Order • ${formatPriceCents(summary.totalCents, currency)}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/** Confirmation after the payment (or the simulated purchase when the server has no Stripe keys). */
@Composable
private fun OrderPlacedDialog(order: CheckoutSession, onDismiss: () -> Unit) {
    val orders = order.orderIds.size
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = Primary) },
        title = { Text(text = "Order placed", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Total paid: ${formatPriceCents(order.totalCents, order.currency)}" +
                            if (orders > 1) " ($orders orders, one per seller)" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (order.shippingCents > 0) {
                    Text(
                        text = "Including ${formatPriceCents(order.shippingCents, order.currency)} of shipping",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Text(
                    text = if (order.simulated) {
                        "Test purchase recorded without payment: the server has no Stripe keys."
                    } else {
                        "Paid in Stripe test mode: no real money was taken. Shipping is simulated."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

/** Stripe result to the app's own type, so the ViewModel does not depend on Stripe. */
private fun PaymentSheetResult.toOutcome(): PaymentOutcome = when (this) {
    is PaymentSheetResult.Completed -> PaymentOutcome.Completed
    is PaymentSheetResult.Canceled -> PaymentOutcome.Canceled
    is PaymentSheetResult.Failed -> PaymentOutcome.Failed(error.message)
    else -> PaymentOutcome.Failed(null)
}