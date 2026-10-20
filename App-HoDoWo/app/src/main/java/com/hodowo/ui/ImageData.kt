package com.hodowo.ui

import android.util.Base64

/**
 * What to give Coil for a guide image.
 *
 * When the annotated image cannot be generated, the backend sends the user's photo
 * back as a `data:image/...;base64,...` URI. Coil 2 cannot load data URIs (only
 * Coil 3 can), so they are decoded here into bytes, which Coil 2 does support.
 * Normal http(s) URLs are returned unchanged.
 */
fun imageData(url: String): Any {
    if (!url.startsWith("data:")) return url

    val base64 = url.substringAfter("base64,", missingDelimiterValue = "")
    if (base64.isEmpty()) return url

    return try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        url
    }
}