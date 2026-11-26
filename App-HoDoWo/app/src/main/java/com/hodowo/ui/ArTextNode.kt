package com.hodowo.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import io.github.sceneview.SceneScope
import io.github.sceneview.material.setTexture
import io.github.sceneview.math.Position
import io.github.sceneview.texture.ImageTexture
import java.util.WeakHashMap

/**
 * A camera-facing text label that can be changed and removed in a live AR scene.
 *
 * Two SceneView 4.18 bugs shape it:
 * - a new text is written to the full-size image only, while a small label on screen is
 *   drawn from the smaller copies (mip levels), so it keeps its first text (fixed in 4.61,
 *   sceneview#4765): every change of text, colour or size builds a new label instead;
 * - a removed label's texture is freed but not its material instance, and Filament then
 *   aborts with "Invalid texture still bound to MaterialInstance" (fixed in 4.52,
 *   sceneview#4285): on removal the instance is pointed at a 1×1 texture that is never
 *   freed, so it stays valid until the engine goes.
 * Newer SceneView needs Kotlin 2.3.
 */
@Composable
internal fun SceneScope.ArTextNode(
    text: String,
    fontSize: Float,
    textColor: Int,
    backgroundColor: Int,
    widthMeters: Float,
    heightMeters: Float,
    position: Position,
    cameraPosition: () -> Position
) {
    key(text, fontSize, textColor, backgroundColor, widthMeters, heightMeters) {
        val held = remember { arrayOfNulls<MaterialInstance>(1) }
        TextNode(
            text = text,
            fontSize = fontSize,
            textColor = textColor,
            backgroundColor = backgroundColor,
            widthMeters = widthMeters,
            heightMeters = heightMeters,
            position = position,
            cameraPositionProvider = cameraPosition,
            apply = { held[0] = materialInstance }
        )
        val engine = this.engine
        DisposableEffect(held) {
            onDispose { held[0]?.let { runCatching { it.setTexture(keptTexture(engine)) } } }
        }
    }
}

private val keptTextures = WeakHashMap<Engine, Texture>()

/** One transparent pixel per engine, never freed: Filament releases it with the engine. */
private fun keptTexture(engine: Engine): Texture = keptTextures.getOrPut(engine) {
    ImageTexture.Builder()
        .bitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        .build(engine)
}
