package com.hodowo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hodowo.R

/**
 * The Howdowo brand mark used in every top banner.
 *
 * @param size edge length of the (square) logo.
 */
@Composable
fun HoDoWoLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    contentDescription: String? = "Howdowo logo"
) {
    Image(
        painter = painterResource(id = R.drawable.ic_hodowo_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size)
    )
}
