package com.isroot.lmbridge.models

import android.graphics.Bitmap

sealed class MultimodalContent {
    data class TextContent(val text: String) : MultimodalContent()
    data class ImageContent(val image: Bitmap) : MultimodalContent()
    data class AudioContent(val audioBytes: ByteArray) : MultimodalContent()
    data class VideoContent(val videoUri: String) : MultimodalContent()
}

data class MultimodalInput(
    val parts: List<MultimodalContent>
) {
    companion object {
        fun text(text: String) = MultimodalInput(listOf(MultimodalContent.TextContent(text)))
    }
}
