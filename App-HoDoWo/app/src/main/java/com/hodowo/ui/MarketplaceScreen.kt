package com.hodowo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val products by viewModel.products.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("all") }

    LaunchedEffect(type) { viewModel.search(query, type) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Marketplace")
            Button(onClick = onPostProduct) { Text("Post to sell") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; viewModel.search(it, type) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search parts, model numbers…") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "new" to "New", "used" to "Second-hand", "part" to "Parts").forEach { (value, label) ->
                FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
            }
        }
        if (loading) CircularProgressIndicator()
        error?.let { Text(it) }
        if (!loading && error == null && products.isEmpty()) Text("No products match these filters.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { ProductCard(it) }
        }
    }
}

@Composable
private fun ProductCard(product: MarketplaceProduct) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (product.sponsored) "Sponsored partner" else product.type.replaceFirstChar { it.uppercase() })
            Text(product.title)
            Text(product.description)
            Text("${product.price} · ${product.partnerName ?: product.sellerName}")
            OutlinedButton(onClick = { }) { Text("Add to cart") }
        }
    }
}

@Composable
fun PostProductScreen(
    viewModel: PostProductViewModel,
    userId: String,
    onPublished: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("used") }
    var images by remember { mutableStateOf(emptyList<android.net.Uri>()) }
    val message by viewModel.message.collectAsState()
    val submitting by viewModel.isSubmitting.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        images = it.take(6)
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Post to sell")
        listOf(
            "Title" to title, "Description" to description, "Category" to category,
            "Brand (optional)" to brand, "Model (optional)" to model, "Price" to price
        ).forEach { (label, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    when (label) {
                        "Title" -> title = next
                        "Description" -> description = next
                        "Category" -> category = next
                        "Brand (optional)" -> brand = next
                        "Model (optional)" -> model = next
                        else -> price = next
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = condition == "used", onClick = { condition = "used" }, label = { Text("Second-hand") })
            FilterChip(selected = condition == "refurbished", onClick = { condition = "refurbished" }, label = { Text("Refurbished") })
        }
        OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Choose photos (${images.size}/6)") }
        message?.let { Text(it) }
        Button(
            enabled = !submitting,
            onClick = { viewModel.submit(userId, title, description, category, brand, model, condition, price, images, onPublished) },
            modifier = Modifier.fillMaxWidth()
        ) { if (submitting) CircularProgressIndicator() else Text("Publish listing") }
    }
}
