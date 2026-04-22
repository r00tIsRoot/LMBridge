package com.isroot.lmbridge.models

import android.graphics.Bitmap

sealed class MultimodalContent {
    data class Text(val text: String) : MultimodalContent()
    data class Image(val bitmap: Bitmap) : MultimodalContent()
    data class Audio(val bytes: ByteArray) : MultimodalContent()
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
