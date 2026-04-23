package com.handy.core.model

import kotlinx.serialization.Serializable

/**
 * A single screenshot attached to an `LlmRequest`.
 *
 * The byte payload is JPEG-encoded (sRGB). The `:android-runtime` capture
 * pipeline handles the encoding off the main thread and passes the finished
 * bytes here — `:core` never touches `android.graphics.Bitmap`.
 */
@Serializable
data class ImagePart(
    /** JPEG bytes (sRGB). Already encoded, ready for base64 by `ClaudeLlmClient`. */
    val jpegBytes: ByteArray,
    /** Short human / LLM-visible label (e.g. `"primary focus (image dimensions: 1440x900 pixels)"`). */
    val label: String,
    /** Original pixel width of the source bitmap. */
    val widthPx: Int,
    /** Original pixel height of the source bitmap. */
    val heightPx: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImagePart) return false
        return label == other.label &&
            widthPx == other.widthPx &&
            heightPx == other.heightPx &&
            jpegBytes.contentEquals(other.jpegBytes)
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + widthPx
        result = 31 * result + heightPx
        result = 31 * result + jpegBytes.contentHashCode()
        return result
    }
}
