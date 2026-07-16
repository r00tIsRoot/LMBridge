package com.isroot.lmbridge.models

import android.graphics.Bitmap

/**
 * 멀티모달 입력의 구성 요소.
 *
 * litertlm `Content`를 노출하지 않는다(어댑터 경계). litertlm 타입으로의 변환은
 * `inference` 레이어의 어댑터가 담당한다.
 */
sealed class MultimodalContent {
    data class Text(val text: String) : MultimodalContent()
    data class Image(val bitmap: Bitmap) : MultimodalContent()
    data class Audio(val bytes: ByteArray) : MultimodalContent() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Audio && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** 로컬 문서 파일. 내용을 컨텍스트로 주입한다. */
    data class Document(val path: String) : MultimodalContent()
}

/**
 * 멀티모달 입력. [parts]의 순서가 모델에 전달되는 순서다.
 *
 * 여러 modality(텍스트·이미지·오디오·문서)를 **혼합**하여 전달할 수 있다.
 */
data class MultimodalInput(
    val parts: List<MultimodalContent>,
) {
    class Builder {
        private val parts = mutableListOf<MultimodalContent>()
        fun text(text: String) = apply { parts.add(MultimodalContent.Text(text)) }
        fun image(bitmap: Bitmap) = apply { parts.add(MultimodalContent.Image(bitmap)) }
        fun audio(bytes: ByteArray) = apply { parts.add(MultimodalContent.Audio(bytes)) }
        fun document(path: String) = apply { parts.add(MultimodalContent.Document(path)) }
        fun build() = MultimodalInput(parts.toList())
    }

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
