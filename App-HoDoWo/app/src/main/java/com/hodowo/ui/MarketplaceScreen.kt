package com.hodowo.ui

import androidx.compose.runtime.Composable
import com.hodowo.viewmodel.MarketplaceViewModel

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
