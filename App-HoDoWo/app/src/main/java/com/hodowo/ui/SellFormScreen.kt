package com.hodowo.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.hodowo.ui.adaptive.AdaptiveContent
import com.hodowo.ui.adaptive.LocalAppLayout
import com.hodowo.ui.components.AppTopBar
import com.hodowo.ui.theme.Error
import com.hodowo.ui.theme.InverseOnSurface
import com.hodowo.ui.theme.InverseSurface
import com.hodowo.ui.theme.OnPrimary
import com.hodowo.ui.theme.OnSurface
import com.hodowo.ui.theme.OnSurfaceVariant
import com.hodowo.ui.theme.OnTertiaryFixed
import com.hodowo.ui.theme.OutlineVariant
import com.hodowo.ui.theme.Primary
import com.hodowo.ui.theme.PrimaryFixed
import com.hodowo.ui.theme.Secondary
import com.hodowo.ui.theme.SecondaryContainer
import com.hodowo.ui.theme.SecondaryFixed
import com.hodowo.ui.theme.Surface
import com.hodowo.ui.theme.SurfaceContainer
import com.hodowo.ui.theme.SurfaceContainerHigh
import com.hodowo.ui.theme.SurfaceContainerLow
import com.hodowo.ui.theme.SurfaceContainerLowest
import com.hodowo.ui.theme.TertiaryFixed
import com.hodowo.viewmodel.ListingDetails
import com.hodowo.viewmodel.ModelScanResult
import com.hodowo.viewmodel.PriceEstimate
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_LISTING_PHOTOS = 8
private const val MIN_TITLE_LENGTH = 3
private const val MIN_DESCRIPTION_LENGTH = 30
private const val MAX_DESCRIPTION_LENGTH = 800

private data class ApplianceType(val label: String, val value: String)

private val APPLIANCE_TYPES = listOf(
    ApplianceType("Washing Machine / Washer", "washing-machine"),
    ApplianceType("Refrigerator / Freezer", "refrigerator"),
    ApplianceType("Espresso & Coffee Maker", "coffee-maker"),
    ApplianceType("Dishwasher", "dishwasher"),
    ApplianceType("Tumble Dryer", "dryer"),
    ApplianceType("Oven / Induction Range", "oven-range"),
    ApplianceType("Other Household Device", "other")
)

private val POPULAR_BRANDS = listOf("Bosch", "Samsung", "LG", "Miele", "Whirlpool", "De'Longhi")

private data class ConditionChoice(
    val value: String,
    val title: String,
    val description: String,
    val badge: String? = null
)

private val CONDITION_CHOICES = listOf(
    ConditionChoice(
        value = "unopened",
        title = "Brand New (Unopened)",
        description = "Original packaging intact. Never installed or tested on a live circuit.",
        badge = "Factory Box"
    ),
    ConditionChoice(
        value = "like-new",
        title = "Like New (Barely used)",
        description = "Installed briefly for troubleshooting or showroom testing. Zero cosmetic scuffs."
    ),
    ConditionChoice(
        value = "used-good",
        title = "Used - Good",
        description = "Fully operational, tested under mechanical load. Normal cosmetic wear that does not affect function.",
        badge = "Recommended"
    ),
    ConditionChoice(
        value = "parts-only",
        title = "For Parts / Not Working",
        description = "Sub-assembly has faults or is defective, but contains salvageable copper, sensors or casings.",
        badge = "Harvesting"
    )
)

/**
 * "Sell an appliance or part" form (marketplace #147/#148).
 *
 * Four steps, one per screen, like Wallapop (review 10-11): photos, what it
 * is, condition and description, price and handover. The AI price estimate is
 * calculated when the user leaves step 3, so the price step already shows it
 * (and fills the price when it is empty). Publishing uploads the photos to Firebase
 * Storage and POSTs the listing to `/api/marketplace/listings`, which stores it
 * in the `listings` collection of the DB diagram.
 */
@Composable
fun PostProductScreen(
    viewModel: com.hodowo.viewmodel.PostProductViewModel,
    userId: String,
    onPublished: () -> Unit,
    onBack: () -> Unit = onPublished,
    onOpenCart: () -> Unit = {}
) {
    var listingKind by rememberSaveable { mutableStateOf("part") }
    var applianceType by rememberSaveable { mutableStateOf(APPLIANCE_TYPES.first().value) }
    var brand by rememberSaveable { mutableStateOf("") }
    var modelCode by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf("used-good") }
    var description by rememberSaveable { mutableStateOf("") }
    var priceInput by rememberSaveable { mutableStateOf("") }
    var pickup by rememberSaveable { mutableStateOf(true) }
    var shipping by rememberSaveable { mutableStateOf(true) }
    var pickupZip by rememberSaveable { mutableStateOf("") }
    var shippingInput by rememberSaveable { mutableStateOf("6.80") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var publishedSuccess by rememberSaveable { mutableStateOf(false) }
    var priceEstimate by remember { mutableStateOf<PriceEstimate?>(null) }
    var pendingLabelPhoto by remember { mutableStateOf<Uri?>(null) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    /** The user tried to continue: the errors of the current step are shown. */
    var stepAttempted by rememberSaveable { mutableStateOf(false) }

    val message by viewModel.message.collectAsState()
    val submitting by viewModel.isSubmitting.collectAsState()
    val polishing by viewModel.isPolishing.collectAsState()
    val estimating by viewModel.isEstimating.collectAsState()
    val scanningLabel by viewModel.isScanningLabel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        images = (images + uris).distinctBy { it.toString() }.take(MAX_LISTING_PHOTOS)
    }

    /** Fills the identification fields with whatever the AI could read. */
    fun applyLabelScan(result: ModelScanResult?) {
        if (result == null) return
        val readBrand = result.brand?.trim().orEmpty()
        val readCode = result.model?.trim().takeUnless { it.isNullOrEmpty() }
            ?: result.partNumber?.trim().takeUnless { it.isNullOrEmpty() }
        if (readBrand.isNotEmpty()) brand = readBrand
        if (readCode != null) modelCode = readCode

        val feedback = when {
            readBrand.isEmpty() && readCode == null ->
                "No model or part number was readable in that photo."
            !result.evidence.isNullOrBlank() -> result.evidence
            readCode != null -> "Read model/part number $readCode"
            else -> "Read brand $readBrand"
        }
        Toast.makeText(context, feedback, Toast.LENGTH_LONG).show()
    }

    val labelCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingLabelPhoto
        pendingLabelPhoto = null
        if (saved && uri != null) viewModel.scanModelLabel(uri, ::applyLabelScan)
    }
    val labelGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.scanModelLabel(uri, ::applyLabelScan)
    }

    fun scanWithCamera() {
        val photo = File(context.cacheDir, "listing-label-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
        pendingLabelPhoto = uri
        labelCamera.launch(uri)
    }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(publishedSuccess) {
        if (publishedSuccess) {
            delay(1_400)
            onPublished()
        }
    }

    val priceValue = priceInput.toDoubleOrNull()
    val shippingValue = if (shipping) shippingInput.toDoubleOrNull() ?: 0.0 else 0.0
    val totalValue = (priceValue ?: 0.0) + shippingValue
    val titleError = (attempted || stepAttempted) && title.trim().length < MIN_TITLE_LENGTH
    val descriptionError = (attempted || stepAttempted) && description.trim().length < MIN_DESCRIPTION_LENGTH
    val priceError = attempted && (priceValue == null || priceValue <= 0)
    val photosError = (attempted || stepAttempted) && images.isEmpty()
    val handoverError = attempted && !pickup && !shipping

    fun listingDetails() = ListingDetails(
        title = title,
        description = description,
        category = APPLIANCE_TYPES.firstOrNull { it.value == applianceType }?.label ?: applianceType,
        brand = brand,
        model = modelCode,
        condition = condition,
        listingKind = listingKind,
        photoCount = images.size,
        pickup = pickup,
        shipping = shipping,
        location = pickupZip
    )

    /** The estimate of the price step; an empty price takes the suggested one. */
    fun estimate() {
        viewModel.estimatePrice(listingDetails()) { result ->
            priceEstimate = result
            if (priceInput.isBlank() && result != null) {
                priceInput = String.format(Locale.US, "%.2f", (result.low + result.high) / 2)
            }
        }
    }

    fun stepIsValid(): Boolean = when (step) {
        0 -> images.isNotEmpty()
        1 -> title.trim().length >= MIN_TITLE_LENGTH
        2 -> description.trim().length >= MIN_DESCRIPTION_LENGTH
        else -> true
    }

    fun next() {
        if (!stepIsValid()) {
            stepAttempted = true
            return
        }
        stepAttempted = false
        step += 1
        // Leaving step 3 everything the price depends on is known: calculate it now.
        if (step == LAST_STEP && priceEstimate == null && !estimating) estimate()
    }

    fun previous() {
        stepAttempted = false
        if (step > 0) step -= 1 else onBack()
    }

    BackHandler(enabled = step > 0 && !publishedSuccess) { previous() }

    fun publish() {
        attempted = true
        val shippingCents = if (shipping) {
            ((shippingInput.toDoubleOrNull() ?: 0.0) * 100).roundToInt()
        } else {
            null
        }
        viewModel.submit(
            userId = userId,
            title = title,
            description = description,
            category = applianceType,
            brand = brand,
            model = modelCode,
            partNumber = modelCode,
            listingKind = listingKind,
            condition = condition,
            priceText = priceInput,
            imageUris = images,
            diagnosticMediaUris = emptyList(),
            handoverMethods = buildList {
                if (pickup) add("pickup")
                if (shipping) add("shipping")
            },
            pickupZip = pickupZip,
            shippingPriceCents = shippingCents,
            onSuccess = { publishedSuccess = true }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Surface).imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                subtitle = "SELL AN APPLIANCE OR PART",
                navigationIcon = {
                    IconButton(onClick = ::previous) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (step > 0) "Previous step" else "Go back",
                            tint = OnSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCart) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Shopping cart",
                            tint = OnSurfaceVariant
                        )
                    }
                }
            )

            StepProgress(step = step)

            AdaptiveContent(modifier = Modifier.weight(1f), maxWidth = 900.dp) {
                val listState = rememberLazyListState()
                // Every step starts from its top.
                LaunchedEffect(step) { listState.scrollToItem(0) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (step == 0) item { AuxiliaryBar() }

                    if (step == 0) item {
                        PhotoSection(
                            images = images,
                            showError = photosError,
                            onAdd = { imagePicker.launch("image/*") },
                            onRemove = { uri -> images = images.filterNot { it == uri } }
                        )
                    }

                    if (step == 1) item {
                        IdentitySection(
                            listingKind = listingKind,
                            onListingKindChange = { listingKind = it },
                            applianceType = applianceType,
                            onApplianceTypeChange = { applianceType = it },
                            brand = brand,
                            onBrandChange = { brand = it },
                            modelCode = modelCode,
                            onModelCodeChange = { modelCode = it },
                            title = title,
                            onTitleChange = { title = it.take(120) },
                            titleError = titleError,
                            scanning = scanningLabel,
                            onScanWithCamera = ::scanWithCamera,
                            onScanFromGallery = { labelGallery.launch("image/*") }
                        )
                    }

                    if (step == 2) item {
                        ConditionSection(
                            condition = condition,
                            onConditionChange = {
                                condition = it
                                priceEstimate = null
                            }
                        )
                    }

                    if (step == 2) item {
                        NarrativeSection(
                            description = description,
                            onDescriptionChange = {
                                description = it.take(MAX_DESCRIPTION_LENGTH)
                                priceEstimate = null
                            },
                            descriptionError = descriptionError,
                            polishing = polishing,
                            onPolish = {
                                viewModel.polishDescription(description) { polished ->
                                    description = polished.take(MAX_DESCRIPTION_LENGTH)
                                }
                            }
                        )
                    }

                    if (step == LAST_STEP) item {
                        PricingSection(
                            priceInput = priceInput,
                            onPriceChange = { priceInput = sanitizeMoney(it) },
                            priceError = priceError,
                            estimate = priceEstimate,
                            estimating = estimating,
                            onEstimate = ::estimate,
                            onUseEstimate = { price ->
                                priceInput = String.format(Locale.US, "%.2f", price)
                            },
                            pickup = pickup,
                            onPickupChange = { pickup = it },
                            shipping = shipping,
                            onShippingChange = { shipping = it },
                            pickupZip = pickupZip,
                            onPickupZipChange = { pickupZip = it.take(20) },
                            shippingInput = shippingInput,
                            onShippingInputChange = { shippingInput = sanitizeMoney(it) },
                            handoverError = handoverError
                        )
                    }
                }
            }

            if (step < LAST_STEP) {
                StepNavigationBar(step = step, onBack = ::previous, onNext = ::next)
            } else PublishBar(
                priceLabel = formatMoney(totalValue),
                breakdown = if (shipping && shippingValue > 0 && priceValue != null) {
                    "Item ${formatMoney(priceValue)} + shipping ${formatMoney(shippingValue)}"
                } else {
                    null
                },
                submitting = submitting,
                published = publishedSuccess,
                onPublish = ::publish
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 96.dp)
        )
    }
}

@Composable
private fun AuxiliaryBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Storefront,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Post to Marketplace",
            style = MaterialTheme.typography.labelLarge,
            color = OnSurface
        )
    }
}

@Composable
private fun PhotoSection(
    images: List<Uri>,
    showError: Boolean,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit
) {
    SectionCard {
        StepHeader(number = 1, title = "Photos & Diagnostic Media") {
            Text(
                text = "${images.size} of $MAX_LISTING_PHOTOS added",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Text(
            text = "Clear photos of serial badges, electrical ports and physical wear speed up sales by 4x.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant
        )
        PhotoGrid(images = images, onAdd = onAdd, onRemove = onRemove)
        if (showError) {
            Text(
                text = "Add at least one photo.",
                style = MaterialTheme.typography.bodySmall,
                color = Error
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    images: List<Uri>,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit
) {
    // Use more columns in landscape / wide layouts so the grid fills horizontal space
    val columns = if (LocalAppLayout.current.isWide) 5 else 3
    val slots = buildList<Uri?> {
        if (images.size < MAX_LISTING_PHOTOS) add(null)
        addAll(images)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { uri ->
                    if (uri == null) {
                        AddPhotoTile(onAdd = onAdd)
                    } else {
                        PhotoTile(
                            uri = uri,
                            isCover = uri == images.firstOrNull(),
                            onRemove = { onRemove(uri) }
                        )
                    }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.AddPhotoTile(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh)
            .clickable(onClick = onAdd),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add photo",
                tint = Primary,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "Add Photo",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RowScope.PhotoTile(uri: Uri, isCover: Boolean, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Listing photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isCover) {
            Text(
                text = "Cover",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceContainerLowest.copy(alpha = 0.92f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(InverseSurface.copy(alpha = 0.82f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove photo",
                tint = InverseOnSurface,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun IdentitySection(
    listingKind: String,
    onListingKindChange: (String) -> Unit,
    applianceType: String,
    onApplianceTypeChange: (String) -> Unit,
    brand: String,
    onBrandChange: (String) -> Unit,
    modelCode: String,
    onModelCodeChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    titleError: Boolean,
    scanning: Boolean,
    onScanWithCamera: () -> Unit,
    onScanFromGallery: () -> Unit
) {
    SectionCard {
        StepHeader(number = 2, title = "Category & Model Identification")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeButton(
                label = "Replacement Part",
                icon = Icons.Default.Build,
                selected = listingKind == "part",
                onClick = { onListingKindChange("part") },
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                label = "Whole Appliance",
                icon = Icons.Default.Kitchen,
                selected = listingKind == "appliance",
                onClick = { onListingKindChange("appliance") },
                modifier = Modifier.weight(1f)
            )
        }

        ApplianceTypeSelect(value = applianceType, onValueChange = onApplianceTypeChange)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormLabel("Brand / Manufacturer")
            OutlinedTextField(
                value = brand,
                onValueChange = { onBrandChange(it.take(80)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. Bosch, Whirlpool, Breville") },
                trailingIcon = if (brand.isNotBlank()) {
                    {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Brand entered",
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(12.dp)
            )
            BrandSuggestions(current = brand, onSelect = onBrandChange)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormLabel("Model Code or Part Number")
            OutlinedTextField(
                value = modelCode,
                onValueChange = { onModelCodeChange(it.take(80)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. WAN28201GB or 00145787") },
                trailingIcon = {
                    LabelScanButton(
                        scanning = scanning,
                        onScanWithCamera = onScanWithCamera,
                        onScanFromGallery = onScanFromGallery
                    )
                },
                supportingText = {
                    Text("Scan the label, sticker or engraved plate to fill this automatically.")
                },
                shape = RoundedCornerShape(12.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FormLabel("Listing Title")
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = titleError,
                placeholder = { Text("e.g. Bosch Serie 4 drain pump motor") },
                supportingText = {
                    Text(
                        text = if (titleError) {
                            "At least $MIN_TITLE_LENGTH characters"
                        } else {
                            "Shown as the headline of your listing"
                        }
                    )
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun RowScope.ModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) SurfaceContainerLowest else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Primary else OnSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Primary else OnSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun ApplianceTypeSelect(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = APPLIANCE_TYPES.firstOrNull { it.value == value } ?: APPLIANCE_TYPES.first()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormLabel("Appliance Classification")
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerLowest)
                    .border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = OnSurfaceVariant
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                APPLIANCE_TYPES.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onValueChange(option.value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandSuggestions(current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Popular:",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
        POPULAR_BRANDS.forEach { brandName ->
            val selected = current.equals(brandName, ignoreCase = true)
            Text(
                text = brandName,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Primary else OnSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) PrimaryFixed else SurfaceContainer)
                    .clickable { onSelect(brandName) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * Scan button of the model/part field: opens a small menu to take a new photo
 * or pick one from the gallery, then the ViewModel sends it to the AI.
 */
@Composable
private fun LabelScanButton(
    scanning: Boolean,
    onScanWithCamera: () -> Unit,
    onScanFromGallery: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = !scanning
        ) {
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan the model or part label",
                    tint = Primary
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Take a photo") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = OnSurfaceVariant
                    )
                },
                onClick = {
                    expanded = false
                    onScanWithCamera()
                }
            )
            DropdownMenuItem(
                text = { Text("Choose from gallery") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = OnSurfaceVariant
                    )
                },
                onClick = {
                    expanded = false
                    onScanFromGallery()
                }
            )
        }
    }
}

@Composable
private fun ConditionSection(condition: String, onConditionChange: (String) -> Unit) {
    SectionCard {
        StepHeader(number = 3, title = "Functional Condition")
        CONDITION_CHOICES.forEach { choice ->
            ConditionCard(
                choice = choice,
                selected = condition == choice.value,
                onClick = { onConditionChange(choice.value) }
            )
        }
    }
}

@Composable
private fun ConditionCard(choice: ConditionChoice, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) SurfaceContainerHigh else SurfaceContainerLowest)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Primary else SurfaceContainerHigh,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Primary)
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = choice.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                choice.badge?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (badge) {
                            "Harvesting" -> OnTertiaryFixed
                            "Recommended" -> Primary
                            else -> Secondary
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                when (badge) {
                                    "Harvesting" -> TertiaryFixed
                                    "Recommended" -> PrimaryFixed
                                    else -> SecondaryFixed
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = choice.description,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun NarrativeSection(
    description: String,
    onDescriptionChange: (String) -> Unit,
    descriptionError: Boolean,
    polishing: Boolean,
    onPolish: () -> Unit
) {
    SectionCard {
        StepHeader(number = null, title = "Item Narrative & Past Repairs") {
            TextButton(
                onClick = onPolish,
                enabled = description.isNotBlank() && !polishing
            ) {
                if (polishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (polishing) "Polishing..." else "AI Polish",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            isError = descriptionError,
            placeholder = {
                Text(
                    "Mention any known issues or past repairs... e.g. 'Pulled from working Bosch " +
                            "Serie 4 after drum bearing repair. Motor runs smooth and quiet at 1400 RPM.'"
                )
            },
            shape = RoundedCornerShape(12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (description.trim().length < MIN_DESCRIPTION_LENGTH) {
                    "Min. $MIN_DESCRIPTION_LENGTH characters"
                } else {
                    "Looks good"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (descriptionError) Error else OnSurfaceVariant
            )
            Text(
                text = "${description.length} / $MAX_DESCRIPTION_LENGTH",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun PricingSection(
    priceInput: String,
    onPriceChange: (String) -> Unit,
    priceError: Boolean,
    estimate: PriceEstimate?,
    estimating: Boolean,
    onEstimate: () -> Unit,
    onUseEstimate: (Double) -> Unit,
    pickup: Boolean,
    onPickupChange: (Boolean) -> Unit,
    shipping: Boolean,
    onShippingChange: (Boolean) -> Unit,
    pickupZip: String,
    onPickupZipChange: (String) -> Unit,
    shippingInput: String,
    onShippingInputChange: (String) -> Unit,
    handoverError: Boolean
) {
    SectionCard {
        StepHeader(number = 4, title = "Pricing & Handover Mode")

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormLabel("Asking Price (USD)")
            OutlinedTextField(
                value = priceInput,
                onValueChange = onPriceChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = priceError,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                leadingIcon = {
                    Text(
                        text = "$",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp)
            )
            if (priceError) {
                Text(
                    text = "Enter an asking price greater than zero.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
        }

        AiEstimateCard(
            estimate = estimate,
            estimating = estimating,
            onEstimate = onEstimate,
            onUseEstimate = onUseEstimate
        )

        FormLabel("Handover / Delivery Methods")

        HandoverCard(
            checked = pickup,
            onCheckedChange = onPickupChange,
            icon = Icons.Default.LocationOn,
            title = "Local Pickup (Free)",
            description = "Buyer inspects and collects directly from your workshop or porch."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = pickupZip,
                    onValueChange = onPickupZipChange,
                    modifier = Modifier.width(160.dp),
                    singleLine = true,
                    label = { Text("Zip Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Shown as the pickup area",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }

        HandoverCard(
            checked = shipping,
            onCheckedChange = onShippingChange,
            icon = Icons.Default.LocalShipping,
            title = "Nationwide Courier Shipping",
            description = "Buyer pays calculated carrier rates. A label is generated upon purchase."
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = shippingInput,
                    onValueChange = onShippingInputChange,
                    modifier = Modifier.width(180.dp),
                    singleLine = true,
                    label = { Text("Shipping (USD)") },
                    leadingIcon = { Text("$", color = OnSurfaceVariant) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Added to the total",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }

        if (handoverError) {
            Text(
                text = "Select at least one handover method.",
                style = MaterialTheme.typography.bodySmall,
                color = Error
            )
        }
    }
}

/** AI valuation box: sends the whole form to the backend and shows its range. */
@Composable
private fun AiEstimateCard(
    estimate: PriceEstimate?,
    estimating: Boolean,
    onEstimate: () -> Unit,
    onUseEstimate: (Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Insights,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "AI price estimate",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Uses the title, description, category, brand, model and condition you entered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }

        estimate?.let { value ->
            Text(
                text = "${formatMoney(value.low)} - ${formatMoney(value.high)} ${value.currency}",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.Bold
            )
            if (value.rationale.isNotBlank()) {
                Text(
                    text = value.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            TextButton(
                onClick = { onUseEstimate((value.low + value.high) / 2) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Use the middle price (${formatMoney((value.low + value.high) / 2)})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Button(
            onClick = onEstimate,
            enabled = !estimating,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (estimating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = OnPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Estimating...", style = MaterialTheme.typography.labelLarge)
            } else {
                Text(
                    text = if (estimate == null) "Calculate a fair price" else "Recalculate",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun HandoverCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceContainerLowest)
            .border(
                width = 1.dp,
                color = if (checked) Primary.copy(alpha = 0.45f) else SurfaceContainerHigh,
                shape = shape
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
        }
        if (checked) {
            Box(modifier = Modifier.padding(start = 28.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun PublishBar(
    priceLabel: String,
    breakdown: String?,
    submitting: Boolean,
    published: Boolean,
    onPublish: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                Text(
                    text = priceLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )
                breakdown?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onPublish,
                enabled = !submitting && !published,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (published) SecondaryFixed else Primary,
                    contentColor = if (published) Secondary else OnPrimary,
                    disabledContainerColor = if (published) SecondaryFixed else Primary.copy(alpha = 0.6f),
                    disabledContentColor = if (published) Secondary else OnPrimary
                )
            ) {
                when {
                    submitting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = OnPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publishing...", style = MaterialTheme.typography.labelLarge)
                    }
                    published -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live on Marketplace!", style = MaterialTheme.typography.labelLarge)
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Publish,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish Community Listing", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLowest,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun StepHeader(number: Int?, title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A second section of the same step has no number of its own.
        if (number != null) Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(PrimaryFixed),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelMedium,
                color = Primary,
                fontWeight = FontWeight.Bold
            )
        }
        if (number != null) Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = OnSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

private fun formatMoney(value: Double): String =
    "$" + String.format(Locale.US, "%.2f", value)

/** Keeps digits and a single decimal separator, at most two decimals. */
private fun sanitizeMoney(raw: String): String {
    val digitsAndDots = raw.filter { it.isDigit() || it == '.' }
    val firstDot = digitsAndDots.indexOf('.')
    val singleDot = if (firstDot < 0) {
        digitsAndDots
    } else {
        digitsAndDots.substring(0, firstDot + 1) +
                digitsAndDots.substring(firstDot + 1).replace(".", "")
    }
    val parts = singleDot.split('.')
    return if (parts.size == 2) {
        parts[0].take(6) + "." + parts[1].take(2)
    } else {
        singleDot.take(6)
    }
}

// ---------- steps ----------

private const val LAST_STEP = 3
private val STEP_TITLES = listOf("Photos", "What it is", "Condition", "Price & delivery")

/** "Step 2 of 4 · What it is" and a bar that fills step by step. */
@Composable
private fun StepProgress(step: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Step ${step + 1} of ${STEP_TITLES.size} · ${STEP_TITLES[step]}",
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STEP_TITLES.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= step) Primary else SurfaceContainerLow)
                )
            }
        }
    }
}

@Composable
private fun StepNavigationBar(step: Int, onBack: () -> Unit, onNext: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step > 0) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (step == LAST_STEP - 1) "Continue to price" else "Continue")
            }
        }
    }
}