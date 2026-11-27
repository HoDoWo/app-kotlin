package com.hodowo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/*
 * The colour roles of the app in the four schemes (#214): open this file in Android
 * Studio to compare them side by side.
 */

@Preview(name = "Light", widthDp = 360)
@Composable
private fun LightPreview() = HoDoWoTheme(darkTheme = false) { ThemeSample() }

@Preview(name = "Dark", widthDp = 360)
@Composable
private fun DarkPreview() = HoDoWoTheme(darkTheme = true) { ThemeSample() }

@Preview(name = "High contrast", widthDp = 360)
@Composable
private fun HighContrastPreview() = HoDoWoTheme(darkTheme = false, highContrast = true) { ThemeSample() }

@Preview(name = "High contrast dark", widthDp = 360)
@Composable
private fun HighContrastDarkPreview() = HoDoWoTheme(darkTheme = true, highContrast = true) { ThemeSample() }

@Composable
private fun ThemeSample() {
    Surface(color = Background) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Title on background", style = MaterialTheme.typography.titleLarge, color = OnBackground)
            Text("Secondary text on background", color = OnSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("Primary") }
                OutlinedButton(onClick = {}) { Text("Outlined") }
                AssistChip(onClick = {}, label = { Text("Chip") })
            }
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Card on surface", fontWeight = FontWeight.SemiBold, color = OnSurface)
                    Text("Supporting text", color = OnSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Selected", PrimaryFixed, Primary)
                Pill("Tip", SecondaryFixed, OnSurface)
                Pill("Second-hand", TertiaryFixed, OnTertiaryFixed)
                Pill("Error", ErrorContainer, OnErrorContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fits", color = Success, fontWeight = FontWeight.SemiBold)
                Text("★★★★☆", color = Rating)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(28.dp).background(OverlayBrand, CircleShape)
                ) { Text("1", color = OnOverlay, fontWeight = FontWeight.Bold) }
                Text("(overlay: same in every theme)", color = OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
