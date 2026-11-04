package com.hodowo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hodowo.data.MarketplaceProduct
import com.hodowo.viewmodel.MarketplaceViewModel
import com.hodowo.viewmodel.PostProductViewModel

@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel,
    onPostProduct: () -> Unit
) {
    ProductSearchAndFiltersScreen(
        viewModel = viewModel,
        onBack = onPostProduct
    )
}

@Composable
fun PostProductScreen(
    viewModel: PostProductViewModel,
    userId: String,
    onPublished: () -> Unit,
    onBack: () -> Unit = onPublished
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var partNumber by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var listingKind by rememberSaveable { mutableStateOf("appliance") }
    var condition by rememberSaveable { mutableStateOf("used-good") }
    var pickup by rememberSaveable { mutableStateOf(true) }
    var shipping by rememberSaveable { mutableStateOf(false) }
    var pickupZip by rememberSaveable { mutableStateOf("") }
    var shippingPrice by rememberSaveable { mutableStateOf("") }
    var images by remember { mutableStateOf(emptyList<android.net.Uri>()) }
    var diagnosticImages by remember { mutableStateOf(emptyList<android.net.Uri>()) }
    val message by viewModel.message.collectAsState()
    val submitting by viewModel.isSubmitting.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        images = it.take(8)
    }
    val diagnosticPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        diagnosticImages = it.take(8)
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sell an appliance or part")
            OutlinedButton(onClick = onBack) { Text("Cancel") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = listingKind == "appliance", onClick = { listingKind = "appliance" }, label = { Text("Appliance") })
            FilterChip(selected = listingKind == "part", onClick = { listingKind = "part" }, label = { Text("Replacement part") })
        }
        listOf(
            "Title" to title, "Description (min. 30 characters)" to description, "Category" to category,
            "Brand (optional)" to brand, "Model code (optional)" to model,
            "Part number (optional)" to partNumber, "Price" to price
        ).forEach { (label, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    when (label) {
                        "Title" -> title = next
                        "Description (min. 30 characters)" -> description = next
                        "Category" -> category = next
                        "Brand (optional)" -> brand = next
                        "Model code (optional)" -> model = next
                        "Part number (optional)" -> partNumber = next
                        else -> price = next
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "unopened" to "Brand new", "like-new" to "Like new",
                "used-good" to "Used - good", "parts-only" to "For parts"
            ).forEach { (value, label) ->
                FilterChip(selected = condition == value, onClick = { condition = value }, label = { Text(label) })
            }
        }
        OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Add photos (${images.size}/8)") }
        OutlinedButton(onClick = { diagnosticPicker.launch("image/*") }) {
            Text("Add diagnostic media (${diagnosticImages.size}/8)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = pickup, onClick = { pickup = !pickup }, label = { Text("Local pickup") })
            FilterChip(selected = shipping, onClick = { shipping = !shipping }, label = { Text("Courier shipping") })
        }
        if (pickup) {
            OutlinedTextField(
                value = pickupZip,
                onValueChange = { pickupZip = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pickup ZIP/postcode") },
                singleLine = true
            )
        }
        if (shipping) {
            OutlinedTextField(
                value = shippingPrice,
                onValueChange = { shippingPrice = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Shipping price (cents, optional)") },
                singleLine = true
            )
        }
        message?.let { Text(it) }
        Button(
            enabled = !submitting,
            onClick = {
                viewModel.submit(
                    userId, title, description, category, brand, model, partNumber, listingKind,
                    condition, price, images, diagnosticImages,
                    buildList { if (pickup) add("pickup"); if (shipping) add("shipping") },
                    pickupZip, shippingPrice.toIntOrNull(), onPublished
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { if (submitting) CircularProgressIndicator() else Text("Publish listing") }
    }
}
