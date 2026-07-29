package com.isroot.lmbridge.models

import android.graphics.Bitmap
import java.io.File
import java.nio.charset.Charset

/**
 * 이미지 인코딩 정책(옵트인).
 *
 * 기본값 [LOSSLESS_PNG]는 무손실 PNG로, 과거 동작과 100% 동일하다. 큰 사진을
 * 그대로 무손실 인코딩하면 수십 MB가 되어 메모리/속도를 낭비하므로, 필요 시
 * [COMPACT_JPEG]처럼 다운스케일·JPEG 인코딩을 명시적으로 선택할 수 있다.
 *
 * @property maxDimension 긴 변 최대 픽셀. 0이면 다운스케일하지 않는다. 이 값을 넘는
 *   이미지만 비율을 유지하며 축소된다(작은 이미지는 그대로).
 * @property format 인코딩 포맷.
 * @property quality 손실 포맷(JPEG) 품질 0..100. PNG에서는 무시된다.
 */
class ImageEncoding(
    val maxDimension: Int = 0,
    val format: Format = Format.PNG,
    val quality: Int = 100,
) {
    enum class Format { PNG, JPEG }

    companion object {
        /** 무손실 PNG, 다운스케일 없음(기본값·과거 동작과 동일). */
        val LOSSLESS_PNG = ImageEncoding()

        /** 긴 변 1024px 다운스케일 + JPEG q90. 온-디바이스 추론에 권장되는 경량값. */
        val COMPACT_JPEG = ImageEncoding(maxDimension = 1024, format = Format.JPEG, quality = 90)
    }
}

/**
 * 멀티모달 입력의 구성 요소.
 *
 * litertlm `Content`를 노출하지 않는다(어댑터 경계). litertlm 타입으로의 변환은
 * `inference` 레이어의 어댑터가 담당한다.
 *
 * 이미지/오디오는 세 가지 소스로 넣을 수 있다:
 * - **비트맵**([Image]) — 디코드된 [Bitmap]. [ImageEncoding]으로 인코딩을 제어한다.
 * - **바이트**([ImageBytes]/[Audio]) — 이미 인코딩된 미디어 바이트(재인코딩 없이 그대로 전달).
 * - **파일 경로**([ImageFile]/[AudioFile]) — 파일을 힙에 적재하지 않고 litertlm에 경로로 전달.
 */
sealed class MultimodalContent {
    data class Text(val text: String) : MultimodalContent()

    /** 디코드된 비트맵. [encoding]으로 인코딩(다운스케일/포맷/품질)을 제어한다. */
    data class Image(
        val bitmap: Bitmap,
        val encoding: ImageEncoding = ImageEncoding.LOSSLESS_PNG,
    ) : MultimodalContent()

    /** 이미 인코딩된 이미지 바이트(JPEG/PNG/WebP 등). 재인코딩 없이 그대로 전달한다. */
    data class ImageBytes(val bytes: ByteArray) : MultimodalContent() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is ImageBytes && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** 로컬 이미지 파일 경로. 힙 적재 없이 경로로 전달한다. */
    data class ImageFile(val path: String) : MultimodalContent()

    /** 이미 인코딩된 오디오 바이트. 재인코딩 없이 그대로 전달한다. */
    data class AudioBytes(val bytes: ByteArray) : MultimodalContent() {
        override fun equals(other: Any?): Boolean =
            this === other || (other is AudioBytes && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** 로컬 오디오 파일 경로. 힙 적재 없이 경로로 전달한다. */
    data class AudioFile(val path: String) : MultimodalContent()

    /**
     * 로컬 텍스트 문서 파일. 내용을 컨텍스트로 주입한다.
     *
     * 텍스트 문서만 지원한다(PDF·오피스 등 바이너리 문서는 지원하지 않으며 오류로 표면화된다).
     * @property charset 파일을 디코드할 문자셋. 기본 UTF-8.
     */
    data class Document(
        val path: String,
        val charset: Charset = Charsets.UTF_8,
    ) : MultimodalContent()
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

        /** 비트맵 이미지. 무손실 PNG로 인코딩한다(기본). */
        fun image(bitmap: Bitmap) = apply { parts.add(MultimodalContent.Image(bitmap)) }

        /** 비트맵 이미지를 [encoding] 정책으로 인코딩하여 추가한다(다운스케일/JPEG 옵트인). */
        fun image(bitmap: Bitmap, encoding: ImageEncoding) =
            apply { parts.add(MultimodalContent.Image(bitmap, encoding)) }

        /** 이미 인코딩된 이미지 바이트를 그대로 추가한다. */
        fun imageBytes(bytes: ByteArray) = apply { parts.add(MultimodalContent.ImageBytes(bytes)) }

        /** 이미지 파일 경로를 추가한다(힙 적재 없음). */
        fun imageFile(path: String) = apply { parts.add(MultimodalContent.ImageFile(path)) }

        /** 이미지 파일을 추가한다(힙 적재 없음). */
        fun imageFile(file: File) = imageFile(file.absolutePath)

        /** 이미 인코딩된 오디오 바이트를 그대로 추가한다. */
        fun audioBytes(bytes: ByteArray) = apply { parts.add(MultimodalContent.AudioBytes(bytes)) }

        @Deprecated(
            "Use audioBytes(bytes) for naming symmetry with imageBytes().",
            ReplaceWith("audioBytes(bytes)"),
        )
        fun audio(bytes: ByteArray) = audioBytes(bytes)

        /** 오디오 파일 경로를 추가한다(힙 적재 없음). */
        fun audioFile(path: String) = apply { parts.add(MultimodalContent.AudioFile(path)) }

        /** 오디오 파일을 추가한다(힙 적재 없음). */
        fun audioFile(file: File) = audioFile(file.absolutePath)

        /** 텍스트 문서 파일. 기본 UTF-8로 디코드하여 내용을 주입한다. */
        fun document(path: String, charset: Charset = Charsets.UTF_8) =
            apply { parts.add(MultimodalContent.Document(path, charset)) }

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
