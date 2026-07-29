package com.isroot.lmbridge.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 이미지 인코딩(다운스케일/포맷/품질) 계측 테스트 — 실제 Android 그래픽 스택이 필요하지만
 * 엔진/모델은 필요 없다. "메모리 폭탄" 수정(무손실 기본 유지 + JPEG 다운스케일 옵트인)을 검증한다.
 */
@RunWith(AndroidJUnit4::class)
class ImageEncoderTest {

    private fun solidBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366CC.toInt()) }

    private fun decodeBounds(bytes: ByteArray): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return opts.outWidth to opts.outHeight
    }

    @Test
    fun losslessPngKeepsDimensionsAndDecodes() {
        val src = solidBitmap(1200, 800)
        val bytes = ImageEncoder.encode(src, ImageEncoding.LOSSLESS_PNG)

        // PNG 시그니처
        assertTrue("must be PNG", bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte())
        assertEquals(1200 to 800, decodeBounds(bytes))
    }

    @Test
    fun compactJpegDownscalesLongEdgeAndShrinksBytes() {
        val src = solidBitmap(4000, 3000)
        val png = ImageEncoder.encode(src, ImageEncoding.LOSSLESS_PNG)
        val jpeg = ImageEncoder.encode(src, ImageEncoding.COMPACT_JPEG)

        // JPEG 시그니처(FF D8)
        assertTrue("must be JPEG", jpeg.size > 3 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte())

        val (w, h) = decodeBounds(jpeg)
        assertEquals("long edge capped at 1024", 1024, maxOf(w, h))
        assertEquals("aspect ratio preserved (4:3)", 768, minOf(w, h))
        assertTrue("compact JPEG must be far smaller than lossless PNG", jpeg.size < png.size)
    }

    @Test
    fun smallImageIsNotUpscaled() {
        val src = solidBitmap(300, 200)
        val bytes = ImageEncoder.encode(src, ImageEncoding.COMPACT_JPEG)
        assertEquals(300 to 200, decodeBounds(bytes))
    }
}
