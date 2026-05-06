package com.isroot.lmbridge.models

import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Content
import java.io.ByteArrayOutputStream

sealed class MultimodalContent {
    data class Text(val text: String) : MultimodalContent()
    data class Image(val bitmap: Bitmap) : MultimodalContent()
    data class Audio(val bytes: ByteArray) : MultimodalContent()

    fun convertToContent(): Content {
        return when(this) {
            is Audio -> Content.AudioBytes(bytes)
            is Image -> Content.ImageBytes(bitmapToByteArray(bitmap))
            is Text -> Content.Text(text)
        }
    }

    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}

data class MultimodalInput(
    val parts: List<MultimodalContent>,
) {
    companion object {
        fun text(prompt: String) = MultimodalInput(listOf(MultimodalContent.Text(prompt)))

        fun textAndImages(prompt: String, images: List<Bitmap>) = MultimodalInput(
            images.map { MultimodalContent.Image(it) } + MultimodalContent.Text(prompt),
        )

        fun textWithContent(instruction: String, content: String) = MultimodalInput(
            listOf(
                MultimodalContent.Text(instruction),
                MultimodalContent.Text(content),
            ),
        )
    }
}
