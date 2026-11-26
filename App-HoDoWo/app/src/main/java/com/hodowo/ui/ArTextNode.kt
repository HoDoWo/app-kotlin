package com.hodowo.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import io.github.sceneview.SceneScope
import io.github.sceneview.material.setTexture
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.TextNode as SceneTextNode
import io.github.sceneview.texture.ImageTexture
import java.util.WeakHashMap

/**
 * A camera-facing text label that can be removed from a live AR scene.
 *
 * SceneView 4.18 never frees a removed TextNode's material instance, but frees its texture
 * a few frames later: Filament then aborts with "Invalid texture still bound to
 * MaterialInstance" (fixed upstream in 4.52, sceneview#4285; 4.52 needs Kotlin 2.3). On
 * removal the left-over instance is pointed at a 1×1 texture that is never freed, so it
 * stays valid until the engine goes. Text, colours and width change in place.
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
    val held = remember { LabelHandles() }
    TextNode(
        text = text,
        fontSize = fontSize,
        textColor = textColor,
        backgroundColor = backgroundColor,
        widthMeters = widthMeters,
        heightMeters = heightMeters,
        position = position,
        cameraPositionProvider = cameraPosition,
        apply = {
            held.node = this
            held.material = materialInstance
            held.width = widthMeters
            held.height = heightMeters
        }
    )
    // The composable reads the size only once: resize the same quad when the text grows.
    SideEffect {
        if (held.width != widthMeters || held.height != heightMeters) {
            held.node?.updateGeometry(size = Size(widthMeters, heightMeters))
            held.width = widthMeters
            held.height = heightMeters
        }
    }
    val engine = this.engine
    DisposableEffect(held) {
        onDispose { held.material?.let { runCatching { it.setTexture(keptTexture(engine)) } } }
    }
}

private class LabelHandles {
    var node: SceneTextNode? = null
    var material: MaterialInstance? = null
    var width = 0f
    var height = 0f
}

private val keptTextures = WeakHashMap<Engine, Texture>()

/** One transparent pixel per engine, never freed: Filament releases it with the engine. */
private fun keptTexture(engine: Engine): Texture = keptTextures.getOrPut(engine) {
    ImageTexture.Builder()
        .bitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        .build(engine)
}
