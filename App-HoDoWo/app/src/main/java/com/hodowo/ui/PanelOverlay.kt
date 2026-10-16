package com.hodowo.ui

import android.graphics.BitmapFactory
import android.net.Uri

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hodowo.viewmodel.ControlType
import com.hodowo.viewmodel.PanelControl

/**
 * Interactive overlay of the controls detected on a control panel (S1-15).
 *
 * Shows the panel photo with a box drawn over every control returned by the
 * backend (S1-14). Tapping a box selects it and the card at the bottom shows
 * what that control does. The chips below the photo are an alternative way of
 * selecting a control, useful when two boxes overlap.
 *
 * @param imageUri photo the analysis ran on (null while none is available).
 * @param controls controls detected by the backend; empty when there are none.
 * @param isLoading true while the backend is still analyzing the photo.
 * @param errorMessage message shown when the analysis failed, if any.
 * @param onClose called when the user taps the close button.
 */
@Composable
fun PanelOverlayScreen(
    imageUri: Uri?,
    controls: List<PanelControl>,
    isLoading: Boolean,
    errorMessage: String?,
    onClose: () -> Unit
) {

    val context = LocalContext.current

    // Photo decoded once per uri; null when it is missing or unreadable.
    val bitmap = remember(imageUri, context) {

        imageUri?.let { uri ->

            runCatching {

                context.contentResolver
                    .openInputStream(uri)
                    ?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }

            }.getOrNull()
        }
    }

    // Control selected by the user (null: none). Cleared when the list changes.
    var selectedId by remember(controls) {
        mutableStateOf<String?>(null)
    }

    val selected = controls.firstOrNull { control ->
        control.id == selectedId
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        /*
         * HEADER
         */
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = "Control panel",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Tap a control on the photo to see what it does.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }


        /*
         * PHOTO + OVERLAY
         * Takes all the space left between the header and the info card.
         */
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {

            when {

                isLoading -> {

                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                bitmap == null -> {

                    Text(
                        text = "Image not available",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {

                    PanelPhotoWithOverlay(
                        bitmap = bitmap,
                        controls = controls,
                        selectedId = selectedId,
                        onSelect = { id ->
                            selectedId = id
                        }
                    )
                }
            }
        }


        /*
         * INFO CARD
         * What the selected control does, plus chips to pick another one.
         */
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 3.dp
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                if (isLoading) {

                    Text(
                        text = "Detecting controls...",
                        style = MaterialTheme.typography.bodyMedium
                    )

                } else if (errorMessage != null && controls.isEmpty()) {

                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                } else if (controls.isEmpty()) {

                    Text(
                        text = "No controls detected in this photo.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                } else {

                    Text(
                        text = "Detected controls (${controls.size})",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        items(
                            items = controls,
                            key = { control ->
                                control.id
                            }
                        ) { control ->

                            FilterChip(
                                selected = control.id == selectedId,
                                onClick = {
                                    selectedId = control.id
                                },
                                label = {
                                    Text(control.label)
                                }
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    if (selected != null) {

                        ControlDetails(control = selected)

                    } else {

                        Text(
                            text = "Tap a box on the photo to learn what it is for.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }


                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                /*
                 * CLOSE
                 */
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text("Back to chat")
                }
            }
        }
    }
}


/*
 * ============================================================
 * PHOTO + CANVAS OVERLAY
 * ============================================================
 */

/**
 * Draws the panel photo with a tappable box over every control.
 *
 * The photo is fitted (kept centred, aspect ratio preserved) and the boxes are
 * scaled to the same rectangle, so they stay aligned at any screen size.
 *
 * @param bitmap decoded panel photo.
 * @param controls controls to draw.
 * @param selectedId id of the highlighted control, if any.
 * @param onSelect called with the id of the tapped control, or null when the
 *   user tapped outside every box.
 */
@Composable
private fun PanelPhotoWithOverlay(
    bitmap: android.graphics.Bitmap,
    controls: List<PanelControl>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {

    // BoxWithConstraints gives the container size in pixels (constraints maxWidth/maxHeight).
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {

        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        val placement = remember(
            bitmap,
            containerWidth,
            containerHeight
        ) {

            fitImagePlacement(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                containerWidth = containerWidth,
                containerHeight = containerHeight
            )
        }

        // The photo itself, fitted and centred like the boxes below.
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Control panel photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Transparent layer on top that draws the boxes and captures the taps.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controls, placement) {

                    detectTapGestures { offset ->

                        onSelect(
                            findControlAt(
                                x = offset.x,
                                y = offset.y,
                                controls = controls,
                                placement = placement
                            )?.id
                        )
                    }
                }
        ) {

            controls.forEach { control ->

                val rect = controlScreenRect(control.box, placement)
                val color = controlColor(control.type)
                val isSelected = control.id == selectedId

                val topLeft = Offset(rect.left, rect.top)
                val size = Size(
                    width = rect.right - rect.left,
                    height = rect.bottom - rect.top
                )

                // Translucent fill so the control under the box stays visible.
                drawRect(
                    color = color.copy(alpha = if (isSelected) 0.30f else 0.12f),
                    topLeft = topLeft,
                    size = size
                )

                // Outline, thicker while the control is selected.
                drawRect(
                    color = color,
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(
                        width = if (isSelected) 6f else 3f
                    )
                )
            }
        }
    }
}


/*
 * ============================================================
 * CONTROL DETAILS
 * ============================================================
 */

/**
 * Name, kind and description of the selected control.
 *
 * @param control control chosen by the user.
 */
@Composable
private fun ControlDetails(control: PanelControl) {

    Text(
        text = control.label,
        style = MaterialTheme.typography.titleMedium
    )

    Text(
        text = control.type.label(),
        style = MaterialTheme.typography.labelMedium,
        color = controlColor(control.type)
    )

    if (control.description.isNotBlank()) {

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = control.description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}


/** Readable name of a control kind ("Button", "Dial"...). */
private fun ControlType.label(): String =
    name.lowercase().replaceFirstChar { character ->
        character.uppercase()
    }

/** Colour used to draw a control, one per kind. */
private fun controlColor(type: ControlType): Color = when (type) {

    ControlType.BUTTON -> Color(0xFF2196F3)
    ControlType.DIAL -> Color(0xFFFF9800)
    ControlType.SWITCH -> Color(0xFF9C27B0)
    ControlType.DISPLAY -> Color(0xFF4CAF50)
    ControlType.OTHER -> Color(0xFF9E9E9E)
}
